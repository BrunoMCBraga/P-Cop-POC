package exceptions;

public class NonExistentApplicationId extends Exception {
	
	public NonExistentApplicationId(String message){
		super(message);
	}

}
