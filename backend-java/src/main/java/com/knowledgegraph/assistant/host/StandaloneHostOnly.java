package com.knowledgegraph.assistant.host;

import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Marker bean can only be installed by the explicit launcher, never a configuration flag. */
final class StandaloneHostOnly implements Condition {
    static final String MARKER="assistantReadStandaloneMarker";
    @Override public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
        return context.getBeanFactory()!=null && context.getBeanFactory().containsSingleton(MARKER);
    }
}
