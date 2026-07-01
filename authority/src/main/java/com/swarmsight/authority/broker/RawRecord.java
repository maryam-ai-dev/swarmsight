package com.swarmsight.authority.broker;

import java.util.Map;

/**
 * What a connector hands back to the broker: the raw field values and the
 * source's own permission per field. Package-private on purpose, so raw values
 * can never leave the broker package without passing through the permission
 * mirror. There is no public type that carries unmasked source data.
 */
record RawRecord(
        String connector,
        String resourceScope,
        Map<String, Object> fields,
        Map<String, FieldEffect> sourcePermissions,
        SourceDocumentRef document) {
}
