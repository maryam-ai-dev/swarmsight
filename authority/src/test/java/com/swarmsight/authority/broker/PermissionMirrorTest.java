package com.swarmsight.authority.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The permission mirror: per field, the intersection of the source permission
 * and the sensitivity policy, with masking applied. Either side can restrict;
 * neither can widen what the other restricts.
 */
class PermissionMirrorTest {

    private final PermissionMirror mirror = new PermissionMirror(new SensitivityPolicy());

    private FieldEffectEntry effect(PermissionMirror.Mirrored m, String field) {
        return m.fieldEffects().stream().filter(e -> e.field().equals(field)).findFirst().orElseThrow();
    }

    @Test
    void housingMapMasksNationalInsuranceAndDeniesMedicalNotes() {
        RawRecord raw = new RawRecord("mock", "tenancy_record",
                Map.of("applicant_name", "Ms A. Adeyemi", "national_insurance", "QQ123456C",
                        "medical_notes", "Disability", "tenancy_status", "confirmed"),
                Map.of("applicant_name", FieldEffect.ALLOW, "national_insurance", FieldEffect.ALLOW,
                        "medical_notes", FieldEffect.ALLOW, "tenancy_status", FieldEffect.ALLOW));

        PermissionMirror.Mirrored m = mirror.apply(raw);

        assertThat(m.maskedFields()).containsEntry("national_insurance", PermissionMirror.MASK_MARKER);
        assertThat(m.maskedFields()).doesNotContainKey("medical_notes");
        assertThat(m.maskedFields()).containsEntry("tenancy_status", "confirmed");
        assertThat(m.maskedFields()).containsEntry("applicant_name", "Ms A. Adeyemi");

        assertThat(effect(m, "national_insurance").outcome()).isEqualTo(FieldEffect.MASK);
        assertThat(effect(m, "medical_notes").outcome()).isEqualTo(FieldEffect.DENY);
    }

    @Test
    void theSourceSideCanRestrictBeyondThePolicy() {
        // Policy allows applicant_name, but the source itself denies it: the
        // intersection denies.
        RawRecord raw = new RawRecord("mock", "s",
                Map.of("applicant_name", "Ms A. Adeyemi"),
                Map.of("applicant_name", FieldEffect.DENY));

        PermissionMirror.Mirrored m = mirror.apply(raw);

        assertThat(m.maskedFields()).doesNotContainKey("applicant_name");
        assertThat(effect(m, "applicant_name").outcome()).isEqualTo(FieldEffect.DENY);
        assertThat(effect(m, "applicant_name").policy()).isEqualTo(FieldEffect.ALLOW);
    }

    @Test
    void anUnmappedFieldFailsClosedToDeny() {
        RawRecord raw = new RawRecord("mock", "s",
                Map.of("secret_field", "value"),
                Map.of("secret_field", FieldEffect.ALLOW));

        PermissionMirror.Mirrored m = mirror.apply(raw);

        assertThat(m.maskedFields()).doesNotContainKey("secret_field");
        assertThat(effect(m, "secret_field").outcome()).isEqualTo(FieldEffect.DENY);
    }

    @Test
    void fieldEffectsAreInDeterministicNameOrder() {
        RawRecord raw = new RawRecord("mock", "s",
                Map.of("zebra", "z", "apple", "a", "mango", "m"),
                Map.of("zebra", FieldEffect.ALLOW, "apple", FieldEffect.ALLOW, "mango", FieldEffect.ALLOW));

        PermissionMirror.Mirrored m = mirror.apply(raw);

        assertThat(m.fieldEffects()).extracting(FieldEffectEntry::field)
                .containsExactly("apple", "mango", "zebra");
    }
}
