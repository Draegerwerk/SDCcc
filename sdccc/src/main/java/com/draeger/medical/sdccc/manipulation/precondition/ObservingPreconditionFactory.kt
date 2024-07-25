package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Injector

interface ObservingPreconditionFactory<PRECONDITION: Observing> {

    fun create(injector: Injector): PRECONDITION

}