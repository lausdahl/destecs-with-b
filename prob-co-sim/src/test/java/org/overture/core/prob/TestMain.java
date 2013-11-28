package org.overture.core.prob;

import java.io.IOException;

import de.be4.classicalb.core.parser.exceptions.BException;
import de.prob.model.classicalb.ClassicalBModel;
import de.prob.scripting.Api;
import de.prob.statespace.OpInfo;
import de.prob.statespace.StateSpace;
import de.prob.statespace.Trace;
import de.prob.webconsole.ServletContextListener;

public class TestMain
{

	/**
	 * @param args
	 * @throws BException
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException, BException
	{
		String filename = "C:\\overture\\runtime-destecs.product-1\\WatertankPeriodic\\model_de\\waterTankCtrl.mch";
		Api api = ServletContextListener.INJECTOR.getInstance(Api.class);
		ClassicalBModel model = api.b_load(filename);
		StateSpace stateSpace = model.getStatespace();
		Trace ctrl = new Trace(stateSpace);
		ctrl = ctrl.anyEvent(null);
		ctrl = ctrl.anyEvent(null);

		int level = 100;
		OpInfo op = stateSpace.opFromPredicate(ctrl.getCurrentState(), "fmiReadInputs", "l="
				+ level, 1).get(0);
		ctrl = ctrl.add(op.id);
		boolean pump = ctrl.getCurrentState().value("fmiPump") == "TRUE";
		System.out.println(pump);

		System.out.println(ctrl);
	}

}
