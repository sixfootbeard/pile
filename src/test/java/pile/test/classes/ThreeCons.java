package pile.test.classes;

public class ThreeCons {
	private final Class<?> clazz;
	public ThreeCons(Object o) { this.clazz = o.getClass(); }
	public ThreeCons(String o) { this.clazz = o.getClass(); }
	public ThreeCons(int o) { this.clazz = int.class; }
	
	public Class<?> clazz() {
		return clazz;
	}
}