package fieldbox.boxes.plugins;

import field.graphics.FLine;
import field.linalg.Vec4;
import field.utility.Cached;
import field.utility.Dict;
import field.utility.Pair;
import field.utility.Rect;
import fieldbox.boxes.*;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Adds: a default decorator for setting a drawer appropriately to give feedback (currently a stripey green frame
 * decoration).
 * <p>
 * This decorator can be retrieved from the property "isExecuting". It has the the signature (box, name) and this
 * decorator stays while there's a callback called name in property Boxes.insideRunLoop
 * <p>
 * see NashornExecution for an example of it's use (other back-ends will need to use this decorator, so it's refactored
 * out here rather than being burried inside NashornExecution).
 */
public class IsExecuting extends Box {

	static public final Dict.Prop<BiConsumer<Box, String>> isExecuting = new Dict.Prop<>("isExecuting").type().toCannon().doc("Given a Box and a name of an run loop, install a drawer that decorates this box (green) while this run loop routine exists");
	static public final Dict.Prop<Integer> executionCount = new Dict.Prop<>("_executionCount").type().toCannon().doc("How many times is this box running? non-zero if this box is currently executing (and is, thus, green).");

	public IsExecuting(Box root_unused) {
		this.properties.put(isExecuting, (box, name) -> {

			box.properties.put(executionCount, 1 + box.properties.computeIfAbsent(executionCount, (k) -> 0));

			box.properties.putToMap(FLineDrawing.frameDrawing, "_animationFeedback_", new Cached<Box, Object, FLine>((b, was) -> {
				Rect rect = box.properties.get(frame);

				if (rect == null) return null;

				Supplier<Boolean> x = b.properties.getFromMap(Boxes.insideRunLoop, name);
				if (x == null) {
					box.properties.put(executionCount, -1 + box.properties.computeIfAbsent(executionCount, (k) -> 0));
					return null;
				}

				FLine f = new FLine();
				f.rect(rect.x, rect.y, rect.w, rect.h);
				f.attributes.put(FLineDrawing.filled, true);
				f.attributes.put(FLineDrawing.fillColor, new Vec4(0.2f, 0.5f, 0.3f, -0.2f));
				f.attributes.put(FLineDrawing.color, new Vec4(0.2f, 0.5f, 0.3f, 0.8f));

				return f;

			}, (b) -> new Pair(b.properties.get(frame), b.properties.getFromMap(Boxes.insideRunLoop, name))));
			Drawing.dirty(box);
		});
	}


}