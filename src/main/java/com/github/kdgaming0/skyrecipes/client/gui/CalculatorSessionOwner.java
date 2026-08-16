package com.github.kdgaming0.skyrecipes.client.gui;

/** Exposes calculator state attached to RRV's item-view overlay mixin. */
public interface CalculatorSessionOwner {
    CalculatorSession skyrecipes$getCalculatorSession();

    void skyrecipes$refreshEffectiveQuery();

    void skyrecipes$reconcileCalculatorConfig();
}
