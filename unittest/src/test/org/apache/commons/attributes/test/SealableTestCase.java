/*
 * Copyright 2003-2004 The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.attributes.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Iterator;
import org.apache.commons.attributes.Attributes;
import org.apache.commons.attributes.AttributeIndex;
import junit.framework.TestCase;

public class SealableTestCase extends TestCase {
    
    public void testSealable () throws Exception {
        Method m = Sample.class.getMethod ("methodWithNamedParameters", new Class[]{ });
        BeanAttribute attribute = (BeanAttribute) Attributes.getAttributes (m, BeanAttribute.class).iterator ().next ();
        
        try {
            attribute.setName ("Joe Doe");
            fail ("Attribute should be sealed!");
        } catch (IllegalStateException ise) {
            // -- OK, attribute should be sealed.
        }
    }
}