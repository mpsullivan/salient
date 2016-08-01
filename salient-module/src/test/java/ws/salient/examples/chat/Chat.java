/*
 * Copyright 2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ws.salient.examples.chat;

import java.util.Collections;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.kie.api.runtime.KieSession;
import ws.salient.examples.chat.Message.Intent;

@Singleton
public class Chat {
    
    private final ResourceBundle messages;
    private final KieSession ksession;

    @Inject
    public Chat(KieSession ksession, Locale locale, ClassLoader classLoader) {
        this.ksession = ksession;
        messages = ResourceBundle.getBundle("ws.salient.examples.chat.messages", locale, classLoader);
    }
    
    public void sayHello() {
        ksession.startProcess("ws.salient.examples.chat.Reply",
                Collections.singletonMap(
                        "reply", new Message(messages.getString("hello"), Intent.HELLO)
                ));
    }
    
    public void sayGoodbye() {
        ksession.startProcess("ws.salient.examples.chat.Reply",
                Collections.singletonMap(
                        "reply", new Message(messages.getString("goodbye"), Intent.GOODBYE)
                ));
    }
    
    
}
