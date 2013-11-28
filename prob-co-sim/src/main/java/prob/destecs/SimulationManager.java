package prob.destecs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.protocol.structs.StepStruct;
import org.destecs.protocol.structs.StepStructoutputsStruct;
import org.destecs.protocol.structs.StepinputsStructParam;

import ch.qos.logback.classic.Level;
import de.be4.classicalb.core.parser.exceptions.BException;
import de.prob.model.classicalb.ClassicalBModel;
import de.prob.scripting.Api;
import de.prob.statespace.OpInfo;
import de.prob.statespace.StateSpace;
import de.prob.statespace.Trace;
import de.prob.webconsole.ServletContextListener;

public class SimulationManager
{

	private static final int CONVERT_FACTOR_DOUBLE = 1000;
	/**
	 * A handle to the unique Singleton instance.
	 */
	static private volatile SimulationManager _instance = null;

	/**
	 * @return The unique instance of this class.
	 */
	static public SimulationManager getInstance()
	{
		if (null == _instance)
		{
			_instance = new SimulationManager();
		}
		return _instance;
	}

	public String getVersion()
	{
		return "0.0.0.1";
	}

	Api api = null;
	StateSpace stateSpace = null;
	Trace ctrl = null;

	public static void setLoggingLevel(Level level)
	{
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		root.setLevel(level);
	}

	public Boolean initialize() throws RemoteSimulationException
	{
		try
		{
			setLoggingLevel(Level.OFF);
			api = ServletContextListener.INJECTOR.getInstance(Api.class);

			// IAnimator animator = ServletContextListener.INJECTOR.getInstance(IAnimator.class);
			// AbstractCommand[] init = {
			// // new LoadBProjectFromStringCommand("MACHINE empty END"),
			// new SetPreferenceCommand("CLPFD", "TRUE"),
			// // new SetPreferenceCommand("BOOL_AS_PREDICATE", "TRUE"),
			// new SetPreferenceCommand("MAXINT", "127"),
			// new SetPreferenceCommand("MININT", "-128") // ,
			// // new SetPreferenceCommand("TIME_OUT", "500"),
			// // new StartAnimationCommand()
			// };
			// animator.execute(init);

			return true;
		} catch (Exception e)
		{
			throw new RemoteSimulationException("Faild to initialize EventB", e);
		}
	}

	private List<File> collectMchFiles(File base)
	{
		List<File> files = new Vector<File>();
		if (base != null)
		{
			if (base.isDirectory())
			{
				for (File file : base.listFiles())
				{
					if (file.getName().endsWith(".mch"))
					{
						files.add(file);
					} else
					{
						files.addAll(collectMchFiles(file));
					}
				}

			}
		}
		return files;
	}

	public Boolean load(File baseDirFile, File outputDir, List<File> specfiles)
			throws RemoteSimulationException
	{
		//

		List<File> mchFiles = collectMchFiles(baseDirFile);

		for (File file : mchFiles)
		{
			String filename = file.getAbsolutePath();
			try
			{
				File f = new File(filename);
				if (!f.exists())
				{
					throw new FileNotFoundException(filename);
				}
				ClassicalBModel model = api.b_load(filename);
				stateSpace = model.getStatespace();
				ctrl = new Trace(stateSpace);
			} catch (Exception e)
			{
				throw new RemoteSimulationException("Faild to load the EventB model: "
						+ filename, e);
			}
		}
		return true;
	}

	public Boolean stopSimulation() throws RemoteSimulationException
	{
		return false;
	}

	public Boolean start(Double finishTime) throws RemoteSimulationException
	{
		try
		{
			// constants

			// AbstractCommand[] init = {
			// new SetPreferenceCommand("CLPFD", "TRUE"),
			// // new SetPreferenceCommand("MAXINT", "127"),
			// new SetPreferenceCommand("MININT", "-128") // ,
			// };
			// ctrl.getStateSpace().execute(init);

			String constraint = "TRUE=TRUE";
			for (Entry<String, Double> entry : sdps.entrySet())
			{
				constraint += " & " + entry.getKey() + " = "
						+ (int) (entry.getValue() * CONVERT_FACTOR_DOUBLE);
			}

			try
			{
				ctrl = callOperation(ctrl, "$setup_constants", constraint);
			} catch (Exception e)
			{
				throw new RemoteSimulationException("Faild to setup constants: "
						+ constraint + " are they declared as NATURALs?", e);
			}
			// ctrl = ctrl.anyEvent(null);
			System.out.println("Calling  any event  "
					+ ctrl.getCurrent().getOp().getName());
			// variable initialization
			ctrl = ctrl.anyEvent(null);
			System.out.println("Calling  any event "
					+ ctrl.getCurrent().getOp().getName());
			return true;
		} catch (Exception e)
		{
			if (e instanceof RemoteSimulationException)
			{
				throw (RemoteSimulationException) e;
			}
			throw new RemoteSimulationException("Faild to start the EventB model", e);
		}
	}

	public List<String> getInputVariables() throws RemoteSimulationException
	{
		return Arrays.asList(new String[] { "level" });
	}

	public List<String> getOutputVariables() throws RemoteSimulationException
	{
		return Arrays.asList(new String[] { "valve" });
	}

	public StepStruct step(Double time, List<StepinputsStructParam> inputs,
			List<String> events) throws RemoteSimulationException
	{
		// Transfer initial values to ports
		double level = 0;
		for (StepinputsStructParam in : inputs)
		{
			if (in.name.equalsIgnoreCase("level"))
			{
				level = in.value.get(0);
			}
		}

		boolean pump = false;

		int blevel = (int) (level * CONVERT_FACTOR_DOUBLE);

		double ctrltime = 0;

		try
		{
			ctrl = callOperation(ctrl, "fmiReadInputs", "l=" + blevel);

			// Let controller decide what action it should perform
			ctrl = callOperation(ctrl, "readLevel");

			ArrayList<String> a = new ArrayList<String>(Arrays.asList(new String[] {
					"switchOn", "switchOff", "switchKeep" }));
			ctrl = ctrl.anyOperation(a);
			System.out.println("Calling  any event (decide) "
					+ ctrl.getCurrent().getOp().getName());
			ctrl = callOperation(ctrl, "writePump");

			// Store Controller's decision on wire
			ctrl = callOperation(ctrl, "fmiWriteOutputs");

			// Finalize step
			ctrltime = ((double) Integer.parseInt(ctrl.getCurrentState().value("time").toString()))
					/ CONVERT_FACTOR_DOUBLE;
			pump = ctrl.getCurrentState().value("fmiPump").equals("TRUE");

			System.out.println("time=" + time + " pump=" + pump + " level="
					+ blevel + " real level=" + level);

		} catch (BException e)
		{
			e.printStackTrace();
			throw new RemoteSimulationException("Unknown failure in step", e);
		}

		List<StepStructoutputsStruct> outputs = new Vector<StepStructoutputsStruct>();

		List<Double> values = new Vector<Double>();
		values.add((pump ? 1.0 : 0.0));

		List<Integer> dimentions = new Vector<Integer>();
		dimentions.add(1);

		outputs.add(new StepStructoutputsStruct("valve", values, dimentions));

		double newTime = new Double(ctrltime);

		while (newTime <= time)
		{
			newTime++;
		}

		StepStruct result = new StepStruct(0, newTime, new Vector<String>(), outputs);

		return result;

	}

	Trace callOperation(Trace trace, String name) throws BException,
			RemoteSimulationException
	{
		return callOperation(trace, name, null);
	}

	Trace callOperation(Trace trace, String name, String predicate)
			throws BException, RemoteSimulationException
	{
		String predicate2 = predicate == null ? "TRUE=TRUE" : predicate;
		// System.out.println("Calling  operation: "+name +" with: "+predicate2);
		List<OpInfo> ops = stateSpace.opFromPredicate(trace.getCurrentState(), name, predicate2, 1);
		if (ops.isEmpty())
		{
			throw new RemoteSimulationException("Unable to find a solution for: "
					+ name + " with predicate: " + predicate2);
		}
		OpInfo op = ops.get(0);
		return trace.add(op.id);
	}

	public List<String> getSharedDesignParameters()
	{
		return Arrays.asList(new String[] { "minlevel", "maxlevel" });
	}

	Map<String, Double> sdps = new HashMap<String, Double>();

	public boolean setDesignParameters(List<Map<String, Object>> parameters)
	{
		for (Map<String, Object> parameter : parameters)
		{
			String parameterName = parameter.get("name").toString();
			Object[] objValue = (Object[]) parameter.get("value");
			// Object[] dimension = (Object[]) parameter.get("size");
			if (objValue.length == 1 && objValue[0] instanceof Double)
			{
				sdps.put(parameterName, (Double) objValue[0]);
				continue;
			} else
			{
				return false;
			}
		}
		return true;
	}
}
