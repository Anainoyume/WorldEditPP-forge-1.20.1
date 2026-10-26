package com.redawn.common.ui;

/** A namespaced texture resource. The bridge owns resource loading and filtering. */
public record UiTexture(String namespace, String path) {
    public UiTexture {
        if (namespace == null || namespace.isBlank() || namespace.indexOf(':') >= 0
                || path == null || path.isBlank() || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Invalid texture id");
        }
    }

    public String id() { return namespace + ":" + path; }
}
