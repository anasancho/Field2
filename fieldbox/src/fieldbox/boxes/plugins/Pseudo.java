package fieldbox.boxes.plugins;

import field.utility.Dict;
import field.utility.IdempotencyMap;
import fieldbox.boxes.Box;
import fieldbox.boxes.Boxes;
import fieldlinker.Linker.AsMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adds properties that help navigate the dispatch tree
 */
public class Pseudo extends Box {

	static public Dict.Prop<FunctionOfBoxValued<First>> where = new Dict.Prop<FunctionOfBoxValued<First>>("where").doc(" _.where.x returns the box that contains the property _.x").toCannon().type();
	static public Dict.Prop<FunctionOfBoxValued<All>> all = new Dict.Prop<FunctionOfBoxValued<All>>("all").doc(" _.all.x returns all values of x above this box").toCannon().type();
	static public Dict.Prop<FunctionOfBoxValued<Has>> has = new Dict.Prop<FunctionOfBoxValued<All>>("has").doc(" _.has.x returns true if this box, or any box above it, has a property x ").toCannon().type();
	static public Dict.Prop<FunctionOfBoxValued<Signal>> signal = new Dict.Prop<FunctionOfBoxValued<All>>("signal").doc(" _.signal.x returns _.has.x, and deletes this value at the same time. ").toCannon().type();

	static public Dict.Prop<FunctionOfBoxValued<Queue>> queue = new Dict.Prop<FunctionOfBoxValued<Queue>>("queue").doc(" _.queue.A = 10, pushes a value to queue A, _.queue.A pops it").toCannon().type();
	static public Dict.Prop<FunctionOfBoxValued<Peek>> peek = new Dict.Prop<FunctionOfBoxValued<Queue>>("peek").doc(" _.peek.A = 10, pushes a value to queue A, _.peek.A peeks at it (returns it without popping)").toCannon().type();

	static public Dict.Prop<FunctionOfBoxValued<Down>> down = new Dict.Prop<FunctionOfBoxValued<Down>>("down").doc(" _.down.x searches for 'x' <i>down</i> the dispatch graph rather than upwards ").toCannon().type();
	static public Dict.Prop<FunctionOfBoxValued<AllDown>> allDown = new Dict.Prop<FunctionOfBoxValued<AllDown>>("allDown").doc(" _.allDown.x searches for 'x' <i>down</i> the dispatch graph rather than upwards, and returns all results").toCannon().type();

	static public Dict.Prop<IdempotencyMap<Runnable>> next= new Dict.Prop<IdempotencyMap<Runnable>>("next").doc(" _.next.A = function(){} executes this function in the next update cycle. Note, 'A' will overwrite anything else that's been set in this box with this name for this cycle").toCannon().type().autoConstructs(() -> new IdempotencyMap<>(Runnable.class));

	public Pseudo(Box r)
	{
		this.properties.put(where, First::new);
		this.properties.put(all, All::new);
		this.properties.put(down, Down::new);
		this.properties.put(allDown, AllDown::new);
		this.properties.put(has, Has::new);
		this.properties.put(signal, Signal::new);
		this.properties.put(queue, Queue::new);
		this.properties.put(peek, Peek::new);

		this.properties.putToMap(Boxes.insideRunLoop, "main.__next__", () -> {
			r.breadthFirst(r.downwards()).map(x -> x.properties.get(next)).filter(x -> x!=null).forEach(x -> {
				x.values().forEach(z -> z.run());
				x.clear();
			});
			return true;
		});
	}

	static public class First implements AsMap
	{

		protected final Box on;

		public First(Box on)
		{
			this.on = on;
		}

		@Override
		public boolean asMap_isProperty(String s) {
			return true;
		}

		@Override
		public Object asMap_call(Object o, Object o1) {
			return null;
		}

		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			return on.breadthFirst(on.upwards()).filter(x -> x.properties.has(p)).findFirst().orElseGet(() -> null);
		}

		@Override
		public Object asMap_set(String s, Object o) {

			if (o instanceof Box)
			{
				Dict.Prop p = new Dict.Prop(s);
				return on.breadthFirst(on.upwards()).filter(x -> x.properties.has(p)).findFirst().map(x -> {
					Object v = x.properties.remove(p);
					((Box)o).properties.put(p, v);
					return v;
				});
			}
			else
			{
				throw new IllegalArgumentException(" can't move property to something that isn't a box");
			}
		}

		@Override
		public Object asMap_new(Object o) {
			return null;
		}

		@Override
		public Object asMap_new(Object o, Object o1) {
			return null;
		}

		@Override
		public Object asMap_getElement(int i) {
			return null;
		}

		@Override
		public Object asMap_setElement(int i, Object o) {
			return null;
		}
	}

	static public class All extends First implements AsMap
	{

		public All(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			return on.breadthFirst(on.upwards()).filter(x -> x.properties.has(p)).map(x -> x.properties.get(p)).collect(Collectors.toList());
		}

	}

	static public class Queue extends First implements AsMap
	{

		public Queue(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop<Collection> p = new Dict.Prop(s);
			Collection q = on.breadthFirst(on.upwards())
						  .filter(x -> x.properties.has(p))
						  .map(x -> x.properties.get(p))
				    .filter(x -> x != null)
						  .filter(x -> x.size() > 0)
						  .findFirst()
						  .orElse(null);
			if (q==null) return null;
			Iterator i = q.iterator();
			Object r = i.next();
			i.remove();
			return r;
		}

		@Override
		public Object asMap_set(String s, Object o) {
			Dict.Prop<Collection<Object>> p = new Dict.Prop<>(s);
			on.properties.putToList(p, o);
			return o;
		}
	}

	static public class Peek extends First implements AsMap
	{

		public Peek(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop<Collection> p = new Dict.Prop(s);
			Collection q = on.breadthFirst(on.upwards())
					 .filter(x -> x.properties.has(p))
					 .map(x -> x.properties.get(p))
					 .filter(x -> x != null)
					 .filter(x -> x.size() > 0)
					 .findFirst()
					 .orElse(null);
			if (q==null) return null;
			Iterator i = q.iterator();
			Object r = i.next();
//			i.remove();
			return r;
		}

		@Override
		public Object asMap_set(String s, Object o) {
			Dict.Prop<Collection<Object>> p = new Dict.Prop<>(s);
			on.properties.putToList(p, o);
			return o;
		}
	}

	static public class Has extends First implements AsMap
	{

		public Has(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			return on.breadthFirst(on.upwards()).filter(x -> x.properties.has(p)).findAny().isPresent();
		}
	}

	static public class Signal extends First implements AsMap
	{

		public Signal(Box on)
		{
			super(on);
		}

		public boolean asMap_isProperty(String s) {
			return true;
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			Optional<Box> q = on.breadthFirst(on.upwards())
					      .filter(x -> x.properties.has(p))
					      .findAny();

			System.out.println(" SIGNAL :"+p+" -> "+q);

			if (!q.isPresent()) return null;

			return q.get().properties.remove(p);
		}


		@Override
		public String toString() {
			return "sig:"+on;
		}
	}

	static public class AllDown extends First implements AsMap
	{

		public AllDown(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			return on.breadthFirst(on.downwards()).filter(x -> x.properties.has(p)).map(x -> x.properties.get(p)).collect(Collectors.toList());
		}

	}

	static public class Down extends First implements AsMap
	{

		public Down(Box on)
		{
			super(on);
		}


		@Override
		public Object asMap_get(String s) {
			Dict.Prop p = new Dict.Prop(s);
			return on.breadthFirst(on.downwards()).filter(x -> x.properties.has(p)).map(x -> x.properties.get(p)).findFirst().orElseGet(() -> null);
		}

	}
}