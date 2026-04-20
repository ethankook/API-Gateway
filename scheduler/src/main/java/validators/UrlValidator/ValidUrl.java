package validators.UrlValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UrlValidator.class)
@Target({ElementType.FIELD})
public @interface ValidUrl {

    String message() default "Invalid URL";

    Class<?>[ ] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
