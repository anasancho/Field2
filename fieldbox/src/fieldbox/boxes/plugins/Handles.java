package fieldbox.boxes.plugins;

import field.graphics.FLine;
import field.graphics.Window;
import field.linalg.Vec2;
import field.utility.Dict;
import field.utility.IdempotencyMap;
import field.utility.Log;
import fieldbox.boxes.Box;
import fieldbox.boxes.Drawing;
import fieldbox.boxes.FLineDrawing;
import fieldbox.boxes.Mouse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Extends FLineInteraction to include, essentially, a node editor. Unlike Field1 this is going to be much more pluggable
 * <p>
 * Rather than add this as a plug-in globally, merely add this above the boxes that you want to add this functionality to.
 */
public class Handles extends Box implements Mouse.OnMouseDown, Mouse.OnMouseMove {

	static public final Dict.Prop<Boolean> editable = new Dict.Prop<Boolean>("editable").type()
											    .toCannon()
											    .doc("Set this to true on an FLine to have it be editable by the handles system");
	static public final Dict.Prop<Handles> handles = new Dict.Prop<Handles>("handles").type()
											  .toCannon()
											  .doc("Provides customizeable node editing for FLines");

	static public final Dict.Prop<IdempotencyMap<Draggable>> draggables = new Dict.Prop<>("draggables").type()
													   .toCannon()
													   .doc("Collection of Draggable instances that offer interactive elements on the canvas. Can be inserted on FLine nodes")
													   .autoConstructs(() -> new IdempotencyMap<Draggable>(Draggable.class));
	static public final Dict.Prop<Boolean> hasDraggables = new Dict.Prop<>("hasDraggables").type()
											       .toCannon()
											       .doc("set to mark that an FLine contains Draggables inside it, and that it should be searched for draggable Nodes");


	public interface SetAndConstrain {
		public Vec2 apply(Vec2 initial, Vec2 previous, Vec2 next);
	}

	static public class Draggable {
		public Vec2 cachePosition = null;
		public Vec2 initialPosition = null;

		public boolean selected = false;

		public Supplier<Vec2> get;
		public SetAndConstrain setAndConstrain;
		public Function<Boolean, Boolean> select;
		public Supplier<List<FLine>> appearance;
		public Consumer<Vec2> finisher;

		public Draggable(Supplier<Vec2> get, SetAndConstrain setAndConstrain, Function<Boolean, Boolean> select, Supplier<List<FLine>> appearance, Consumer<Vec2> finisher) {
			this.get = get;
			this.setAndConstrain = setAndConstrain;
			this.select = select;
			this.appearance = appearance;
			this.finisher = finisher;
			init();
		}

		protected void init() {
			cachePosition = initialPosition = get.get();
		}

		protected Draggable() {
		}

		public Vec2 getPosition() {
			if (cachePosition == null) return cachePosition = setAndConstrain.apply(initialPosition, get.get(), get.get());
			return cachePosition;
		}

		public Vec2 getInitialPosition() {
			return initialPosition;
		}

		public void set(Vec2 v) {
			cachePosition = setAndConstrain.apply(initialPosition, getPosition(), v);
		}

		public void select(boolean to) {
			selected = select.apply(to);
		}

		public void finish() {
			finisher.accept(getPosition());
			initialPosition = getPosition();
		}

		public boolean isSelected() {
			return selected;
		}
	}

	public Handles(Box root) {
		properties.putToMap(Mouse.onMouseDown, "__handles__", this);
		properties.putToMap(Mouse.onMouseMove, "__handles__", this);
		properties.putToMap(FLineDrawing.bulkLines, "__handles__", this::appearence);
	}

	/**
	 * this needs to get behind a 'cached'
	 *
	 * @return
	 */
	public List<Draggable> all() {
		List<Draggable> l1 = breadthFirst(downwards()).filter(x -> x != this)
							      .filter(x -> x.properties.has(FLineDrawing.lines))
							      .flatMap(x -> x.properties.get(FLineDrawing.lines)
											.values()
											.stream())
							      .map(x -> x.get())
							      .filter(x -> x != null)
							      .filter(x -> x.attributes.isTrue(hasDraggables, false))
							      .flatMap(x -> x.nodes.stream())
							      .filter(x -> x.attributes.has(draggables))
							      .flatMap(x -> x.attributes.get(draggables)
											.values()
											.stream())
							      .collect(Collectors.toList());
		int sz;
		List<Draggable> l2 = l1;
		do {

			sz = l1.size();
			 l2 = l2.stream()
					       .flatMap(x -> x.appearance.get()
									 .stream())
					       .filter(x -> x.attributes.isTrue(hasDraggables, false))
					       .flatMap(x -> x.nodes.stream())
					       .filter(x -> x.attributes.has(draggables))
					       .flatMap(x -> x.attributes.get(draggables)
									 .values()
									 .stream())
					       .collect(Collectors.toList());

			l1.addAll(l2);

		} while (l1.size() != sz);
		return l1;
	}

	/**
	 * this needs to get behind a 'cached'
	 *
	 * @return
	 */
	public List<FLine> appearence() {
		List<FLine> a = all().stream()
				     .flatMap(x -> x.appearance.get()
							       .stream())
				     .collect(Collectors.toList());
		if (a.size()>0)
			Log.log("handles", "appearence is :" + a);
		return a;
	}

	@Override
	public Mouse.Dragger onMouseDown(Window.Event<Window.MouseState> e, int button) {
		List<Draggable> d = all();
		float r = 15;

		Optional<Drawing> drawing = this.find(Drawing.drawing, both())
						.findFirst();
		Vec2 pos = drawing.map(x -> x.windowSystemToDrawingSystem(new Vec2(e.after.x, e.after.y)))
				  .orElseThrow(() -> new IllegalArgumentException(" can't mouse around something without drawing support (to provide coordinate system)"));


		Log.log("handles", "onMouseDown :" + pos);

		Draggable selected = d.stream()
				      .filter(x -> x.getPosition()
						    .distanceFrom(pos) < r)
				      .sorted((a, b) -> -Double.compare(a.getPosition()
									 .distanceFrom(pos), b.getPosition()
											      .distanceFrom(pos)))
				      .findFirst()
				      .orElse(null);

		Log.log("handles", "selected :" + selected);

		if (selected == null) {
			d.stream()
			 .forEach(x -> {
				 if (x != selected && x.selected) x.select(false);
			 });
			Drawing.dirty(this);
			return null;
		}

		if (e.after.keyboardState.isShiftDown()) {
			if (selected.selected) {
				selected.select(false);
			} else {
				selected.select(true);
			}
		} else {
			d.stream()
			 .forEach(x -> {
				 if (x != selected && x.selected) x.select(false);
			 });
			selected.select(true);
		}

		Set<Draggable> sel = d.stream()
				      .filter(x -> x.selected)
				      .collect(Collectors.toSet());

		Log.log("handles", "selected set :" + selected);

		Drawing.dirty(this);
		e.properties.put(Window.consumed, true);

		return draggerForSelection(e, sel);

	}

	private Mouse.Dragger draggerForSelection(Window.Event<Window.MouseState> e, Set<Draggable> sel) {
		Optional<Drawing> drawing = this.find(Drawing.drawing, both())
						.findFirst();
		return (drag, term) -> {

			try {
				Vec2 deltaNow = drawing.map(x -> x.windowSystemToDrawingSystemDelta(new Vec2(drag.after.x - e.after.x, drag.after.y - e.after.y)))
						       .orElseThrow(() -> new IllegalArgumentException(" cant mouse around something without drawing support (to provide coordinate system)"));

				drag.properties.put(Window.consumed, true);

				sel.forEach(d -> {
					d.set(Vec2.add(d.getInitialPosition(), deltaNow, null));
				});

				if (term) {
					sel.forEach(Draggable::finish);
				}
				Drawing.dirty(this);
				return !term;
			}
			catch(Throwable t)
			{
				t.printStackTrace();
				return false;
			}
		};
	}

	@Override
	public Mouse.Dragger onMouseMove(Window.Event<Window.MouseState> e) {
		return null;
	}


}