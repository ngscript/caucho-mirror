package javax.jws;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * XXX: temp for compile only, please replace.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebParam {
  public enum Mode {
    IN,
    OUT,
    INOUT
  };

  String name() default "";
  String partName() default "";
  String targetNamespace() default "";
  Mode mode() default Mode.IN;
  boolean header() default false;
};

