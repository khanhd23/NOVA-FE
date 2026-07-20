package com.nova.app.core.backend

object BackendRuntimeRegistry {
    @Volatile
    var runtime: BackendRuntime? = null
}
