/*
 * Copyright 2012 Research Studios Austria Forschungsges.m.b.H. Licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package won.bot.debugbot.event;

import org.apache.jena.query.Dataset;
import won.bot.debugbot.enums.HintType;
import won.bot.framework.eventbot.event.BaseAtomSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.atomlifecycle.AtomCreatedEvent;

import java.net.URI;

/**
 *
 */
public class AtomCreatedEventForDebugHint extends AtomCreatedEvent {
    private final HintType hintType;
    private final Event cause;

    public AtomCreatedEventForDebugHint(Event cause, final URI atomURI, final URI wonNodeUri,
                                        final Dataset atomDataset,
                                        final HintType hintType) {
        super(atomURI, wonNodeUri, atomDataset, null);
        this.cause = cause;
        this.hintType = hintType;
    }

    public HintType getHintType() {
        return hintType;
    }

    public Event getCause() {
        return cause;
    }
}
