/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

/**
 * POJO for persisting headers.
 */
@Entity(name = "MdibVersionGroupEntity")
@Table(name = "mdib_version_groups")
public class MdibVersionGroupEntity {

    @Id
    @GenericGenerator(name = "MdibVersionGroupIDGen", strategy = "increment")
    @GeneratedValue(generator = "MdibVersionGroupIDGen")
    private long incId;

    private long mdibVersion;
    private String sequenceId;
    private String bodyElement;

    @ManyToOne(fetch = FetchType.LAZY)
    private MessageContent messageContent;

    /**
     * This will be used by hibernate when creating the POJO from database entries.
     */
    public MdibVersionGroupEntity() {}

    /**
     * This will be used when creating the POJO before loading it into the database.
     *
     * @param mdibVersionGroup record containing version of the MDIB,
     *                         an identifier of the sequence and the respective body element
     * @param messageContent   row to link to
     */
    public MdibVersionGroupEntity(final MdibVersionGroup mdibVersionGroup, final MessageContent messageContent) {
        this.mdibVersion = mdibVersionGroup.mdibVersion();
        this.sequenceId = mdibVersionGroup.sequenceId();
        this.bodyElement = mdibVersionGroup.bodyElement();
        this.messageContent = messageContent;
    }

    public long getMdibVersion() {
        return this.mdibVersion;
    }

    public String getSequenceId() {
        return this.sequenceId;
    }

    public String getBodyElement() {
        return this.bodyElement;
    }

    /**
     * Stores selected MdibVersionGroup attributes and also a string representation of the respective elements QName.
     *
     * @param mdibVersion MdibVersion attribute value converted to long
     * @param sequenceId  SequenceId attribute value
     * @param bodyElement string representation of the respective elements QName
     */
    public record MdibVersionGroup(long mdibVersion, @Nullable String sequenceId, String bodyElement) {}
}
