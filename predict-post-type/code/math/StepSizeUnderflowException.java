/**
 * 
 */
package math;

public class StepSizeUnderflowException extends RuntimeException
  {

		public double[] x;

		public double[] deriv;

		/**
		 * @param message
		 */
		public StepSizeUnderflowException(String message, double[] x, double[] deriv)
		{
			super(message);
			this.x = x;
			this.deriv = deriv;
			// TODO Auto-generated constructor stub
		}

		/**
	 * 
	 */
		private static final long serialVersionUID = 3005842700483094903L;

  }