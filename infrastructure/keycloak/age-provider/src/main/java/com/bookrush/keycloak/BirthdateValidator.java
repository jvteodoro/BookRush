package com.bookrush.keycloak;

import com.bookrush.platform.core.AgePolicy;
import java.time.Clock;
import java.util.List;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.validate.SimpleValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

public final class BirthdateValidator implements SimpleValidator {
  @Override
  public String getId() {
    return "bookrush-birthdate";
  }

  @Override
  public ValidationContext validate(Object input, String field, ValidationContext validation, ValidatorConfig config) {
    var attribute = UserProfileAttributeValidationContext.from(validation).getAttributeContext();
    var profile = attribute.getContext();
    var values = (List<?>) input;
    if (profile != UserProfileContext.REGISTRATION && !profile.isAdminContext()) {
      var user = attribute.getUser();
      List<String> existing = user == null ? List.of() : user.getAttributeStream(field).toList();
      if (!values.equals(existing)) {
        validation.addError(new ValidationError(getId(), field, "error-user-attribute-read-only"));
      }
      return validation;
    }
    if (values.isEmpty()) {
      if (profile == UserProfileContext.REGISTRATION) {
        validation.addError(new ValidationError(getId(), field, "error-user-attribute-required"));
      }
      return validation;
    }
    if (values.size() != 1 || !(values.getFirst() instanceof String value)
        || AgePolicy.validDate(value, Clock.systemUTC()).isEmpty()) {
      validation.addError(new ValidationError(getId(), field, "error-invalid-value"));
    }
    return validation;
  }
}
