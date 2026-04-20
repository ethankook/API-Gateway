package validators.CronValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CronValidator.class)
@Documented
public @interface ValidCron {
    String message() default "Invalid cron expression";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
