import java.util.Arrays;

public class RecordPatterns{
	
	record Point(int x, int y){}
	record Point3(int x, int y, int z){}
	record S<A, B, Xs>(A a, B b, Xs... xs){}
	enum Colour{ RED, GREEN, BLUE }
	
	sealed interface Shape{}
	
	record Triangle(Point a, Point b, Point c) implements Shape{}
	record Rectangle(Point a, Point b) implements Shape{}
	record Circle(Point c, int r) implements Shape{}
	
	record ColouredShape(Shape it, Colour c){}
	
	public static void main(String[] args){
		Shape shape = fromText(args);
		System.out.println(switch(shape){
			case Triangle(Point(var x, var y) a, Point(int x2, int y2), Point c) t
			when (x + y > 0) -> t + "";
			case Triangle t -> t;
			case Rectangle r -> r;
			case Circle c
			when c != null -> c;
			case Circle c -> "null circle lol";
			case null -> "null2";
		});
		
		var p3 = fromTextP(args);
		
		if(p3 instanceof Point3(int x, int y, int z)){
			System.out.println(x + y + z);
		}
		
		if(p3 instanceof Point3(int a, int b, int c) d && a > 0 && b == 2){
			System.out.println(c);
		}
		
		var s = fromTextS(args);
		
		if(s instanceof S<?,?,?>(Integer i, String st, Object[] os)){
			System.out.println("an S of " + i + " and " + st + " and " + Arrays.toString(os));
		}
	}
	
	public static Shape fromText(String[] s){
		return null;
	}
	
	public static Point3 fromTextP(String[] s){
		return null;
	}
	
	public static S fromTextS(String[] s){
		return null;
	}
}