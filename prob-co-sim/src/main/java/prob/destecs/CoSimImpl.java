/*******************************************************************************
 * Copyright (c) 2010, 2011 DESTECS Team and others.
 *
 * DESTECS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DESTECS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DESTECS.  If not, see <http://www.gnu.org/licenses/>.
 * 	
 * The DESTECS web-site: http://destecs.org/
 *******************************************************************************/
package prob.destecs;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.destecs.protocol.IDestecs;
import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.protocol.structs.GetDesignParametersStruct;
import org.destecs.protocol.structs.GetDesignParametersStructdesignParametersStruct;
import org.destecs.protocol.structs.GetParametersStruct;
import org.destecs.protocol.structs.GetParametersStructparametersStruct;
import org.destecs.protocol.structs.GetStatusStruct;
import org.destecs.protocol.structs.GetVariablesStruct;
import org.destecs.protocol.structs.GetVersionStruct;
import org.destecs.protocol.structs.LoadpropertiesStructParam;
import org.destecs.protocol.structs.QueryInterfaceStruct;
import org.destecs.protocol.structs.QueryInterfaceStructinputsStruct;
import org.destecs.protocol.structs.QueryInterfaceStructoutputsStruct;
import org.destecs.protocol.structs.QueryInterfaceStructsharedDesignParametersStruct;
import org.destecs.protocol.structs.StepStruct;
import org.destecs.protocol.structs.StepinputsStructParam;

@SuppressWarnings("unchecked")
public class CoSimImpl implements IDestecs
{
	static int stepCount = 0;

	private static final String simulatorMame = "ProB Solver";
	private static final String LOAD_FILE = "file";
	private static final String LOAD_BASE_DIR = "basedir";
	public static final String LOAD_OUTPUT_DIR = "output_dir";
	private String interfaceVersion = "3.0.4.0";
	public static boolean DEBUG = false;;
	private static Double finishTime = 0.0;

	public Map<String, Integer> getStatus()
	{
		return new GetStatusStruct(0).toMap();
	}

	public Map<String, Object> getVersion()
	{

		return new GetVersionStruct(interfaceVersion, simulatorMame, SimulationManager.getInstance().getVersion()).toMap();
	}

	public Boolean initialize() throws RemoteSimulationException
	{
		stepCount = 0;
		try
		{
			return SimulationManager.getInstance().initialize();
		} catch (RemoteSimulationException e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public Boolean load(Map<String, List<Map<String, Object>>> data)
			throws RemoteSimulationException
	{
		Object o = data.get("properties");
		Object[] oo = (Object[]) o;

		List<File> specfiles = new Vector<File>();
		File baseDirFile = null;
		File outputDir = null;
		try
		{
			for (Object in : oo)
			{
				if (in instanceof Map)
				{
					LoadpropertiesStructParam arg = new LoadpropertiesStructParam((Map<String, Object>) in);

					if (arg.key.startsWith(LOAD_FILE))
					{
						specfiles.add(new File(arg.value));
					} else if (arg.key.startsWith(LOAD_BASE_DIR))
					{
						baseDirFile = new File(arg.value);
					} else if (arg.key.startsWith(LOAD_OUTPUT_DIR))
					{
						outputDir = new File(arg.value);
						ErrorLog.outputFolder = outputDir;
					}
				}
			}

			return SimulationManager.getInstance().load(baseDirFile, outputDir, specfiles);
		} catch (RemoteSimulationException e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public Map<String, Object> queryInterface()
			throws RemoteSimulationException
	{
		/*
		 * Shared design variables minLevel maxLevel Variables level :IN valveState :OUT Events HIGH_LEVEL LOW_LEVEL
		 */
		QueryInterfaceStruct s = new QueryInterfaceStruct();

		for (String sdp : SimulationManager.getInstance().getSharedDesignParameters())
		{

			// dimension does not matter at this point
			List<Integer> dimensions = new Vector<Integer>();
			dimensions.add(1);
			s.sharedDesignParameters.add(new QueryInterfaceStructsharedDesignParametersStruct(sdp, dimensions));
		}

		for (String input : SimulationManager.getInstance().getInputVariables())
		{
			List<Integer> dimensions = new Vector<Integer>();
			dimensions.add(1);
			s.inputs.add(new QueryInterfaceStructinputsStruct(input, dimensions));

		}

		for (String output : SimulationManager.getInstance().getOutputVariables())
		{
			List<Integer> dimensions = new Vector<Integer>();
			dimensions.add(1);
			s.outputs.add(new QueryInterfaceStructoutputsStruct(output, dimensions));
		}

		// No events from VDM

		return s.toMap();
	}

	public Map<String, Object> step(Map<String, Object> data)
			throws RemoteSimulationException
	{
		try
		{
			stepCount++;
			Double outputTime = (Double) data.get("outputTime");

			List<Object> tmp = Arrays.asList((Object[]) data.get("inputs"));

			List<StepinputsStructParam> inputs = new Vector<StepinputsStructParam>();
			for (Object in : tmp)
			{
				if (in instanceof Map)
				{
					inputs.add(new StepinputsStructParam((Map<String, Object>) in));
				}
			}

			List<Object> tmp1 = Arrays.asList((Object[]) data.get("events"));

			List<String> events = new Vector<String>();
			for (Object in : tmp1)
			{
				if (in instanceof String)
				{
					events.add((String) in);
				}
			}

			StepStruct result;

			result = SimulationManager.getInstance().step(outputTime, inputs, events);

			if (result.time > finishTime)
			{
				// The next point where VDM needs CT communication is result.time but if the simulation stops before we
				// are not by protocol allowed to ask for this.
				result.time = finishTime;
			}

			return result.toMap();
		} catch (RemoteSimulationException e)
		{
			ErrorLog.log(e);
			throw e;
		} catch (Exception e)
		{
			ErrorLog.log(e);
			throw new RemoteSimulationException("Failure in step", e);
		}
	}

	public Boolean terminate()
	{
		System.out.println("The B solver is terminating now...");

		Thread shutdown = new Thread(new Runnable()
		{

			public void run()
			{
				try
				{
					Thread.sleep(1000);
				} catch (InterruptedException e)
				{
					// Wait for terminate to reply to client then terminate
				}
				CoSim.shutdown();
			}
		});
		if (!DEBUG)
		{
			shutdown.start();
		}
		return true;
	}

	public Boolean stop() throws RemoteSimulationException
	{
		try
		{
			System.out.println("Total steps taken: " + stepCount);
			return SimulationManager.getInstance().stopSimulation();
		} catch (RemoteSimulationException e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public Map<String, List<Map<String, Object>>> getDesignParameters(
			List<String> data) throws RemoteSimulationException
	{
		List<GetDesignParametersStructdesignParametersStruct> list = new Vector<GetDesignParametersStructdesignParametersStruct>();
		// for (Entry<String, LinkInfo> entry : SimulationManager.getInstance().getSharedDesignParameters().entrySet())
		// {
		// list.add(SimulationManager.getInstance().getDesignParameter(entry.getKey()));
		// }
		return new GetDesignParametersStruct(list).toMap();
	}

	public Map<String, List<Map<String, Object>>> getParameters(
			List<String> data) throws RemoteSimulationException
	{
		List<GetParametersStructparametersStruct> list = new Vector<GetParametersStructparametersStruct>();
		// try
		// {
		// for (String name : data)
		// {
		// // list.add(new GetParametersStructparametersStruct(name,
		// // SimulationManager.getInstance().getParameter(name),
		// // SimulationManager.getInstance().getParameterSize(name)));
		// }
		//
		// } catch (RemoteSimulationException e)
		// {
		// throw e;
		// }
		return new GetParametersStruct(list).toMap();
	}

	public Boolean setDesignParameters(
			Map<String, List<Map<String, Object>>> data)
			throws RemoteSimulationException
	{
		// try
		// {
		boolean success = false;
		if (data.values().size() > 0)
		{
			Object s = data.values().iterator().next();
			@SuppressWarnings("rawtypes")
			List tmp = Arrays.asList((Object[]) s);
			success = SimulationManager.getInstance().setDesignParameters(tmp);

		}
		return success;
		// } catch (RemoteSimulationException e)
		// {
		// throw e;
		// }
	}

	/**
	 * Local method
	 * 
	 * @param data
	 * @return
	 * @throws RemoteSimulationException
	 */
	private Boolean setParameter(Map<String, Object> data)
			throws RemoteSimulationException
	{
		// String name = (String) data.get("name");
		List<Double> value = new Vector<Double>();

		for (Object o : (Object[]) data.get("value"))
		{
			if (o instanceof Double)
			{
				value.add((Double) o);
			} else
			{
				throw new RemoteSimulationException("Internal error converting parameter: "
						+ o + " to Double");
			}
		}

		List<Integer> size = new Vector<Integer>();

		for (Object o : (Object[]) data.get("size"))
		{
			if (o instanceof Integer)
			{
				size.add((Integer) o);
			} else
			{
				throw new RemoteSimulationException("Internal error converting parameter size: "
						+ o + " to Integer");
			}
		}

		Boolean success;
		// try
		// {
		success = true;// SimulationManager.getInstance().setInstanceVariable(name, new ValueContents(value, size));

		return success;
		// } catch (RemoteSimulationException e)
		// {
		// // ErrorLog.log(e);
		// throw e;
		// }
	}

	public Boolean setParameters(Map<String, List<Map<String, Object>>> data)
			throws Exception
	{
		try
		{
			boolean success = true;

			if (data.values().size() > 0)
			{
				Object t = data.get("parameters");
				for (Object parms : (Object[]) t)
				{
					Map<String, Object> s = (Map<String, Object>) parms;
					success = success && setParameter(s);
				}
				return success;
			}
			return success;
		} catch (Exception e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public Boolean start(Map<String, Object> data)
			throws RemoteSimulationException
	{
		try
		{
			finishTime = (Double) data.get("finishTime");
			// long internalFinishTime = SystemClock.timeToInternal(TimeUnit.seconds, finishTime);
			return SimulationManager.getInstance().start(finishTime);
		} catch (RemoteSimulationException e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public Boolean setLogVariables(Map<String, Object> data) throws Exception
	{
		try
		{
			List<String> logVariables = new Vector<String>();

			for (Object o : (Object[]) data.get("variables"))
			{
				logVariables.add(o.toString());
			}

			// SimulationManager.getInstance().setLogVariables(new File(data.get("filePath").toString()), logVariables);

			return true;
		} catch (Exception e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public List<Map<String, Object>> queryVariables() throws Exception
	{
		List<Map<String, Object>> result = new Vector<Map<String, Object>>();
		// for (String name : SimulationManager.getInstance().getLogEnabledVariables())
		// {
		// Hashtable<String, Object> table = new Hashtable<String, Object>();
		// table.put("name", name);
		// table.put("size", new Integer[]{});
		// result.add(table);
		// }
		return result;
	}

	public List<Map<String, Object>> queryParameters() throws Exception
	{
		List<Map<String, Object>> result = getParameters(new Vector<String>()).get("parameters");
		for (Map<String, Object> map : result)
		{
			if (map.containsKey("value"))
			{
				map.remove("value");
			}
		}
		return result;
	}

	public Boolean suspend() throws Exception
	{
		try
		{
			// BasicSchedulableThread.signalAll(Signal.SUSPEND);
			return false;
		} catch (Exception e)
		{
			ErrorLog.log(e);
			throw new RemoteSimulationException("Failed to suspend the VDM debugger");
		}

	}

	/**
	 * This method is just a skip, we need to resume from the IDE. This is needed because the IDE hold a state
	 * representing the state of the threads
	 */
	public Boolean resume() throws Exception
	{
		return true;
	}

	public Boolean setSettings(List<Map<String, Object>> data) throws Exception
	{
		return true;
	}

	public List<Map<String, Object>> querySettings(List<String> data)
			throws Exception
	{
		try
		{
			List<Map<String, Object>> settings = new Vector<Map<String, Object>>();

			return settings;
		} catch (Exception e)
		{
			ErrorLog.log(e);
			throw e;
		}
	}

	public List<Map<String, Object>> queryImplementations() throws Exception
	{
		return new Vector<Map<String, Object>>();
	}

	public Boolean setImplementations(List<Map<String, Object>> data)
			throws Exception
	{
		return false;
	}

	public Map<String, List<Map<String, Object>>> getVariables(List<String> data)
			throws Exception
	{
		GetVariablesStruct vars = new GetVariablesStruct();
		// try
		// {
		// // for (Entry<String, ValueContents> p :
		// // SimulationManager.getInstance().getInstanceVariables(data).entrySet())
		// // {
		// // vars.variables.add(new GetVariablesStructvariablesStruct(p.getKey(), p.getValue().value,
		// // p.getValue().size));
		// // }
		// } catch (RemoteSimulationException e)
		// {
		// throw e;
		// }

		return vars.toMap();
	}

	public Boolean saveRequired() throws Exception
	{
		return false;
	}

}
