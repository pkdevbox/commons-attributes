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
package org.apache.commons.attributes;

/**
 * Thrown when one of the {@link Attributes}.getAttribute methods find more
 * than one instance of the specified attribute class.
 */
public class MultipleAttributesError extends Error {
    
    public MultipleAttributesError (String clazz) {
        super ("There was more than one attribute of class " + clazz + " associated with the element.");
    }
    
}