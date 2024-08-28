/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/
package com.ericsson.oss.adc.emsnc.it;

import java.util.Set;

public class AllowedTypes {
  private AllowedTypes() {
    // intentionally empty
  }

  private static final Set<Class<?>> WRAPPER_TYPES =
      Set.of(
          Boolean.class,
          Character.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          Void.class);

  private static boolean isWrapperType(Class<?> clazz) {
    return WRAPPER_TYPES.contains(clazz);
  }

  public static boolean isAllowed(Class<?> aClass) {
    // could support simple data classes if needed
    return aClass.isPrimitive() || aClass.equals(String.class) || isWrapperType(aClass);
  }
}
