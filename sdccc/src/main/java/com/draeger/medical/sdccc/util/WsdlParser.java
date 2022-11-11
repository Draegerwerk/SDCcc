/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.model.TDefinitions;
import org.somda.sdc.dpws.wsdl.model.TMessage;
import org.somda.sdc.dpws.wsdl.model.TOperation;
import org.somda.sdc.dpws.wsdl.model.TParam;
import org.somda.sdc.dpws.wsdl.model.TPart;
import org.somda.sdc.dpws.wsdl.model.TPortType;

/**
 * Class used to determine the interface described by a WSDL.
 */
public class WsdlParser {
    private static final Logger LOG = LogManager.getLogger();

    private final WsdlMarshalling wsdlMarshalling;

    @Inject
    WsdlParser(final WsdlMarshalling wsdlMarshalling) {
        this.wsdlMarshalling = wsdlMarshalling;
    }

    /**
     * Parses all portTypes contained in a Wsdl String into operations with input and output elements.
     *
     * @param wsdl to retrieve portTypes from
     * @return map containing one entry for each portType. PortType name is the key, value is another map for
     *         operations. The key is the operation name, value is a container holding arguments
     *         for input and output elements used by the operation, resolved to the wsdl:message parts.
     * @throws javax.xml.bind.JAXBException in case wsdl cannot be serialized
     */
    public Map<QName, Map<QName, OperationArguments>> parseWsdlPortTypes(final String wsdl)
            throws javax.xml.bind.JAXBException {
        final var tDef = wsdlMarshalling.unmarshal(new ByteArrayInputStream(wsdl.getBytes(StandardCharsets.UTF_8)));

        final var targetNamespace = tDef.getTargetNamespace();

        final var portTypes = tDef.getAnyTopLevelOptionalElement().stream()
                .filter(any -> any instanceof TPortType)
                .map(any -> (TPortType) any)
                .collect(Collectors.toList());

        final Map<QName, Map<QName, OperationArguments>> portTypeMap = new HashMap<>();

        for (final TPortType portType : portTypes) {
            final var portTypeName = new QName(targetNamespace, portType.getName());
            final var operations = portType.getOperation();
            LOG.debug("Parsing portType {}", portTypeName);

            final Map<QName, OperationArguments> operationArguments = new HashMap<>();

            for (final TOperation operation : operations) {

                final List<OperationArgument> opArgs = new ArrayList<>();
                for (var op : operation.getRest()) {
                    if (Constants.WSDL_INPUT.equals(op.getName())) {
                        final QName inputOp = ((TParam) op.getValue()).getMessage();
                        opArgs.add(new OperationArgument(
                                ArgumentType.INPUT,
                                resolveMessagePart(tDef, inputOp).get(0)));
                    } else if (Constants.WSDL_OUTPUT.equals(op.getName())) {
                        final QName outputOp = ((TParam) op.getValue()).getMessage();
                        opArgs.add(new OperationArgument(
                                ArgumentType.OUTPUT,
                                resolveMessagePart(tDef, outputOp).get(0)));
                    } else {
                        throw new IllegalStateException(
                                "encountered an unexpected element " + "within an WSDL operation: " + op.getName());
                    }
                }

                operationArguments.put(
                        new QName(targetNamespace, operation.getName()),
                        new OperationArguments(opArgs.toArray(new OperationArgument[] {})));
            }

            LOG.debug("portType {} has operations {}", portTypeName, operationArguments.keySet());
            portTypeMap.put(portTypeName, operationArguments);
        }

        return portTypeMap;
    }

    private List<QName> resolveMessagePart(final TDefinitions wsdl, @Nullable final QName messageName) {
        if (messageName == null) {
            return Collections.emptyList();
        }

        final var tns = wsdl.getTargetNamespace();

        final var messages = wsdl.getAnyTopLevelOptionalElement().stream()
                .filter(element -> element instanceof TMessage)
                .map(element -> (TMessage) element)
                .filter(element -> messageName.equals(new QName(tns, element.getName())))
                .collect(Collectors.toList());

        assert messages.size() == 1;

        final var message = messages.get(0);

        return message.getPart().stream().map(TPart::getElement).collect(Collectors.toList());
    }

    enum ArgumentType {
        INPUT,
        OUTPUT
    }

    /**
     * Operation input or output part QNames holder.
     */
    public static class OperationArgument {
        private final ArgumentType type;
        private final List<QName> arguments;

        OperationArgument(final ArgumentType type, final QName... arguments) {
            this.type = type;
            this.arguments = Arrays.asList(arguments);
        }

        public ArgumentType getType() {
            return type;
        }

        public List<QName> getArguments() {
            return Collections.unmodifiableList(arguments);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final OperationArgument that = (OperationArgument) o;
            return type == that.type && Objects.equals(arguments, that.arguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, arguments);
        }
    }

    /**
     * Operation input and output part QNames holder.
     */
    public static class OperationArguments {

        private final List<OperationArgument> operationArguments;

        OperationArguments(final OperationArgument... operationArguments) {
            this.operationArguments = Arrays.asList(operationArguments);
        }

        public List<OperationArgument> getOperationArguments() {
            return Collections.unmodifiableList(this.operationArguments);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final OperationArguments that = (OperationArguments) o;
            if (this.operationArguments.size() != that.operationArguments.size()) {
                return false;
            }
            return Objects.equals(this.operationArguments, that.operationArguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationArguments);
        }
    }
}
