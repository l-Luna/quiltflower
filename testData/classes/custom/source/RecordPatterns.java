

public class RecordPatterns{
	
	record Point(int x, int y){}
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
	}
	
	public static Shape fromText(String[] s){
		return null;
	}
}