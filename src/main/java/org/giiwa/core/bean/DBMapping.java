/*
 * Copyright 2015 Giiwa, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.core.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TODO: Auto-generated Javadoc
/**
 * the {@code DBMapping} Class used to annotate the Bean, define the
 * collection/table mapping with the Bean
 * 
 * <pre>
 * db, default empty="prod", used to define the db name which defined in giiwa.properties
 * table, the table of mapped
 * collection, the collection of mapped, when collection defined, will ignore the table
 * </pre>
 * 
 * @author joe
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DBMapping {

    /**
     * the db name, default is EMPTY="prod".
     *
     * @return String
     */
    String db() default X.EMPTY;

    /**
     * the table name.
     *
     * @return String
     */
    String table() default X.EMPTY;

    /**
     * the collection name.
     *
     * @return String
     */
    String collection() default X.EMPTY;

}
