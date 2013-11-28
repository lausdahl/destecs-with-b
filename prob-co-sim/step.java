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

		int blevel = (int) (level * 1000);

		int ctrltime = 0;

		try
		{
			pump = ctrl.getCurrentState().value("fmiPump") == "TRUE";
			pump = !pump;// negate the B model

			System.out.println("time=" + time + " pump=" + pump + " level="
					+ blevel);

			ctrl = callOperation(ctrl, "fmiReadInputs", "l=" + blevel);

			// Let controller decide what action it should perform
			ctrl = callOperation(callOperation(callOperation(ctrl, "readLevel"), "decide"), "writePump");

			ctrltime = ((Integer) ctrl.getCurrentState().value("time")) / 1000;

			// Store Controller's decision on wire
			ctrl = callOperation(ctrl, "fmiWriteOutputs");

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