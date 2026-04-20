package validators.UrlValidator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

  @Override
  public boolean isValid(String url, ConstraintValidatorContext ctx) {
    if (url == null) return true;

    try {
      URI uri = new URI(url);
      return uri.getScheme() != null
          && uri.getHost() != null
          && (uri.getScheme().equals("http") || uri.getScheme().equals("https"));
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
