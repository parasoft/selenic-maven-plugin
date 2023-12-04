/*
 * Copyright 2023 Parasoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parasoft.selenic.maven.plugin;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class Messages {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(Messages.class.getName());

    private Messages() {
    }

    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    public static String get(String key, Object... data) {
        String message = get(key);
        if (message != null && !message.isEmpty()) {
            MessageFormat formatter = new MessageFormat("");
            try {
                formatter.applyPattern(message);
                message = formatter.format(data);
            } catch (IllegalArgumentException e) {
                // ignored
            }
        }
        return message;
    }
}
