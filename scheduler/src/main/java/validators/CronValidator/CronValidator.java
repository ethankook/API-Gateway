package validators.CronValidator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

public class CronValidator implements ConstraintValidator<ValidCron, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true; // null handled by @NotBlank if required
        try {
            CronExpression.parse(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}