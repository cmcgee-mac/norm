/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use the SQL annotation to provide the SQL statement statically to a NORM
 * statement object.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = ElementType.TYPE_USE)
public @interface SQL {

    String value();

}
