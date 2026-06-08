package com.julia_auto_cars.rental_api.automation.flow;

import com.julia_auto_cars.rental_api.automation.action.FlowAction;

import java.util.List;

/**
 * A registered flow: a name, the event that triggers it, and the ordered
 * list of actions to run.
 */
public record FlowDefinition(
        String name,
        String triggerEvent,
        String description,
        List<FlowAction> actions
) {}
