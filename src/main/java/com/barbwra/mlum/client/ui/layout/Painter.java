package com.barbwra.mlum.client.ui.layout;

import com.barbwra.mlum.client.ui.Canvas;

/** Custom drawing for a node, in its laid-out rectangle. */
@FunctionalInterface
public interface Painter {
    void paint(Canvas canvas, Node node);
}
