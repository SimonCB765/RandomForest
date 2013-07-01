package analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import datasetgeneration.CrossValidationFoldGenerationMultiClass;

import tree.Forest;
import tree.ImmutableTwoValues;
import tree.ProcessDataForGrowing;
import tree.TreeGrowthControl;

public class WeightAndMtryOptimisation
{

	/**
	 * Used in the optimisation of the mtry parameter and the weights of the individual classes.
	 * 
	 * @param args - The file system locations of the files and directories used in the optimisation.
	 */
	public static void main(String[] args)
	{
		String inputFile = args[0];  // The location of the dataset used to grow the forests.
		String resultsDir = args[1];  // The location where the results and records of the optimisation will go.
		String testFileLocation = null;  // The location of a dataset to test on the forests grown, but not to use in their growing.
		if (args.length >= 3)
		{
			// Only record an actual location if there are at least three argument supplied.
			testFileLocation = args[2];
		}
		main(inputFile, resultsDir, testFileLocation);
	}

	/**
	 * @param inputFile - The location of the dataset used to grow the forests.
	 * @param resultsDir - The location where the results and records of the optimisation will go.
	 * @param testFileLocation - The location of a dataset to test on the forests grown, but not to use in their growing.
	 */
	public static void main(String inputFile, String resultsDir, String testFileLocation)
	{
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================
		int numberOfForestsToCreate = 100;  // The number of forests to create for each weight/mtry combination.
		int cvFoldsToUse = 10;  // The number of cross validation folds to use if cross validation is being used.
		Integer[] mtryToUse = {5, 10, 15, 20, 25, 30};  // The different values of mtry to test.
		Integer[] trainingObsToUse = {};  // The observations in the training set that will be used in growing the forests.

		// varyingClassWeightMapping can be used when the weights for a class should be varied, while constantClassWeightMapping can be used to assign a weight to
		// any class that will not have its weight varied.
		Map<String, Double[]> varyingClassWeightMapping = new HashMap<String, Double[]>();  // A mapping from class names to the weights that will be tested for the class.
		varyingClassWeightMapping.put("Positive", new Double[]{1.0, 2.0, 3.0});
		varyingClassWeightMapping.put("PossiblePositive", new Double[]{1.0, 2.0, 3.0});
		Map<String, Double> constantClassWeightMapping = new HashMap<String, Double>();  // A mapping from class names to the weight that will be used for the class.
		constantClassWeightMapping.put("Unlabelled", 1.0);

		// Default parameters for the tree growth and input dataset processing controller object.
		TreeGrowthControl ctrl = new TreeGrowthControl();
		ctrl.isReplacementUsed = true;
		ctrl.numberOfTreesToGrow = 1000;  // The number of trees in each forest.
		ctrl.isStratifiedBootstrapUsed = true;
		ctrl.isCalculateOOB = true;  // Set this to false to use cross-validation, or true to use OOB observations.
		ctrl.minNodeSize = 1;
		ctrl.trainingObservations = Arrays.asList(trainingObsToUse);

		// isPredictionAveragingUsed controls how to handle the predictions on the test dataset (if one is supplied). If true, then the stats (G mean,
		// MCC, F measure, etc.) will be calculated separately for the OOB observations in the input dataset and the observations in the test dataset.
		// The two sets of stats will then be averaged. If false, the confusion matrix for the predictions of the OOB observations will be combined with
		// the confusion matrix generated by the prediction of the observations in the test dataset, and the stats for this combined confusion
		// matrix will be calculated.
		boolean isPredictionAveragingUsed = false; 
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================

		// Setup the directory for the results.
		File resultsDirectory = new File(resultsDir);
		if (!resultsDirectory.exists())
		{
			boolean isDirCreated = resultsDirectory.mkdirs();
			if (!isDirCreated)
			{
				System.out.println("The results directory could not be created.");
				System.exit(0);
			}
		}
		else if (!resultsDirectory.isDirectory())
		{
			// Exists and is not a directory.
			System.out.println("The second argument must be a valid directory location or location where a directory can be created.");
			System.exit(0);
		}

		// Process the input dataset.
		ProcessDataForGrowing procData = new ProcessDataForGrowing(inputFile, ctrl);

		// Determine the classes in the input dataset.
		List<String> classesInDataset = new ArrayList<String>(new HashSet<String>(procData.responseData));
		Collections.sort(classesInDataset);

		// Initialise the results, parameters and controller object record files.
		String resultsLocation = resultsDir + "/Results.txt";
		String parameterLocation = resultsDir + "/Parameters.txt";
		String controllerLocation = resultsDir + "/ControllerUsed.txt";
		try
		{
			// Setup the results file.
			FileWriter resultsOutputFile = new FileWriter(resultsLocation);
			BufferedWriter resultsOutputWriter = new BufferedWriter(resultsOutputFile);
			String seondHeader = "\t\t\t\t\t\t\t\t\t";
			for (String s : classesInDataset)
			{
				resultsOutputWriter.write(s + "Weight\t");
				seondHeader += "\t";
			}
			resultsOutputWriter.write("Mtry\tGMean\tMCC\tF0.5\tF1\tF2\tAccuracy\tError\tTimeTakenPerRepetition(ms)\t");
			for (String s : classesInDataset)
			{
				resultsOutputWriter.write(s + "\t\t");
			}
			resultsOutputWriter.newLine();
			resultsOutputWriter.write(seondHeader);
			for (String s : classesInDataset)
			{
				resultsOutputWriter.write("True\tFalse\t");
			}
			resultsOutputWriter.newLine();
			resultsOutputWriter.close();

			// Record the parameters.
			FileWriter parameterOutputFile = new FileWriter(parameterLocation);
			BufferedWriter parameterOutputWriter = new BufferedWriter(parameterOutputFile);
			parameterOutputWriter.write("Number of forests grown - " + Integer.toString(numberOfForestsToCreate));
			parameterOutputWriter.newLine();
			if (testFileLocation == null)
			{
				parameterOutputWriter.write("No test set used");
			}
			else
			{
				parameterOutputWriter.write("Test set is used.");
				parameterOutputWriter.newLine();
				parameterOutputWriter.write("\tG mean averaging is ");
				if (!isPredictionAveragingUsed)
				{
					parameterOutputWriter.write("not ");
				}
				parameterOutputWriter.write("used");
			}
			parameterOutputWriter.newLine();
			if (ctrl.isCalculateOOB)
			{
				parameterOutputWriter.write("CV not used");
				parameterOutputWriter.newLine();
			}
			else
			{
				parameterOutputWriter.write("CV used with " + Integer.toString(cvFoldsToUse) + " folds");
				parameterOutputWriter.newLine();
			}
			parameterOutputWriter.write("Weights used");
			parameterOutputWriter.newLine();
			for (String s : constantClassWeightMapping.keySet())
			{
				parameterOutputWriter.write("\t" + s + " - " + Double.toString(constantClassWeightMapping.get(s)));
				parameterOutputWriter.newLine();
			}
			for (String s : varyingClassWeightMapping.keySet())
			{
				parameterOutputWriter.write("\t" + s + " - " + Arrays.toString(varyingClassWeightMapping.get(s)));
				parameterOutputWriter.newLine();
			}
			parameterOutputWriter.write("Mtry used - " + Arrays.toString(mtryToUse));
			parameterOutputWriter.newLine();
			parameterOutputWriter.write("Training observations used - " + Arrays.toString(trainingObsToUse));
			parameterOutputWriter.newLine();
			parameterOutputWriter.close();

			ctrl.save(controllerLocation);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}

		// Generate all the random seeds to use in growing the forests. The same numberOfForestsToCreate seeds will be used for every weight/mtry
		// combination. This ensures that the only difference in the results is due to the chosen weight/mtry combination.
		Random randGen = new Random();
		List<Long> seeds = new ArrayList<Long>();
		for (int i = 0; i < numberOfForestsToCreate; i++)
		{
			long seedToUse = randGen.nextLong();
			while (seeds.contains(seedToUse))
			{
				seedToUse = randGen.nextLong();
			}
			seeds.add(seedToUse);
		}

		// Generate the cross validation folds if required.
		String cvFoldLocation = resultsDir + "/CVFolds-Repetition";
		if (!ctrl.isCalculateOOB)
		{
			for (int i = 0; i < numberOfForestsToCreate; i++)
			{
				String repCvFoldLoc = cvFoldLocation + Integer.toString(i);
				File cvFoldDir = new File(repCvFoldLoc);
				if (!cvFoldDir.exists())
				{
					boolean isDirCreated = cvFoldDir.mkdirs();
					if (!isDirCreated)
					{
						System.out.println("The CV fold directory does not exist, and could not be created.");
						System.exit(0);
					}
				}
				CrossValidationFoldGenerationMultiClass.main(inputFile, repCvFoldLoc, cvFoldsToUse);
			}
		}

		// Determine a fixed ordering for the classes that are going to have their weight varied.
		List<String> orderedClassesWithVaryingWeights = new ArrayList<String>(varyingClassWeightMapping.keySet());
		Collections.sort(orderedClassesWithVaryingWeights);

		// Initialise the maps for controlling the optimisation.
		Map<String, Integer> classWeightIndexMapping = new HashMap<String, Integer>();  // Maps each class s to the index of the weight in varyingClassWeightMapping.get(s) currently assigned to it.
		Map<String, Integer> finalWeightIndexMapping = new HashMap<String, Integer>();  // Maps each class to the maximum possible index it can take.
		for (String s : orderedClassesWithVaryingWeights)
		{
			classWeightIndexMapping.put(s, 0);
			finalWeightIndexMapping.put(s, varyingClassWeightMapping.get(s).length - 1);
		}

		Map<String, Double> weights = new HashMap<String, Double>();  // The weight mapping that will actually be used when growing the forest.
		for (String s : constantClassWeightMapping.keySet())
		{
			weights.put(s, constantClassWeightMapping.get(s));
		}

		for (Integer mtry : mtryToUse)
		{
			System.out.format("Now working on mtry - %d.\n", mtry);

			ctrl.mtry = mtry;

			boolean isJustStarted = true;  // Determines whether the analysis of this mtry has just begun.
										   // Used as the termination condition is the same as the starting condition (all values in classWeightIndexMapping == 0).

			while (!terminationReached(classWeightIndexMapping) || isJustStarted)
			{
				System.out.print("\tNow working on weights : ");
				for (String s : classWeightIndexMapping.keySet())
				{
					Double classWeight = varyingClassWeightMapping.get(s)[classWeightIndexMapping.get(s)];
					weights.put(s, classWeight);
					System.out.print(s + "-" + Double.toString(classWeight) + "\t");
				}
				System.out.println();

				isJustStarted = false;

				Map<String, Double> statistics = new HashMap<String, Double>();  // The record of the statistics of the predictions
				Map<String, Map<String, Double>> confusionMatrix = new HashMap<String, Map<String, Double>>();  // The confusion matrix of the predictions.
				long timeTaken;
				if (ctrl.isCalculateOOB)
				{
					// If cross validation is not being used.
					ImmutableTwoValues<Map<String, Map<String, Map<String, Double>>>, Long> results = generateForestsNoCV(weights, ctrl, inputFile, seeds, numberOfForestsToCreate, testFileLocation);
					confusionMatrix = results.first.get("Training");
					Map<String, Map<String, Double>> testConfusionMatrix = results.first.get("Testing");
					timeTaken = results.second;

					// Process the prediction results.
					if (testFileLocation != null)
					{
						// If a test set is being used.
						if (isPredictionAveragingUsed)
						{
							Map<String, Double> oobStats = calculateStats(confusionMatrix, numberOfForestsToCreate, inputFile);
							Map<String, Double> testStats = calculateStats(testConfusionMatrix, numberOfForestsToCreate, testFileLocation);

							// Average the statistics.
							for (String s : oobStats.keySet())
							{
								statistics.put(s, (oobStats.get(s) + testStats.get(s)) / 2.0);
							}
						}
						else
						{
							// Aggregate the confusion matrices from the oob and test set predictions.
							for (String s : confusionMatrix.keySet())
							{
								for (String p : confusionMatrix.get(s).keySet())
								{
									Double oobValue = confusionMatrix.get(s).get(p);
									Double testValue = testConfusionMatrix.get(s).get(p);
									confusionMatrix.get(s).put(p, oobValue + testValue);
								}
							}
							statistics = calculateStats(confusionMatrix, numberOfForestsToCreate, inputFile, testFileLocation);
						}
					}
					else
					{
						// No test set is being used.
						statistics = calculateStats(confusionMatrix, numberOfForestsToCreate, inputFile);
					}
				}
				else
				{
					// If cross validation is being used.
					ImmutableTwoValues<Map<String, Map<String, Double>>, Long> results = generateForestsWithCV(weights, ctrl, cvFoldLocation, inputFile, seeds, numberOfForestsToCreate, cvFoldsToUse);
					confusionMatrix = results.first;
					timeTaken = results.second;
					statistics = calculateStats(confusionMatrix, numberOfForestsToCreate, inputFile);
				}

				// Write out the statistics for this mtry/weight combination.
				try
				{
					FileWriter resultsOutputFile = new FileWriter(resultsLocation, true);
					BufferedWriter resultsOutputWriter = new BufferedWriter(resultsOutputFile);
					for (String s : classesInDataset)
					{
						resultsOutputWriter.write(String.format("%.5f", weights.get(s)));
						resultsOutputWriter.write("\t");
					}
					resultsOutputWriter.write(Integer.toString(ctrl.mtry));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("GMean")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("MCC")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("F0.5")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("F1")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("F2")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", 1 - statistics.get("ErrorRate")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(String.format("%.5f", statistics.get("ErrorRate")));
					resultsOutputWriter.write("\t");
					resultsOutputWriter.write(Long.toString(timeTaken));
					resultsOutputWriter.write("\t");
					for (String s : classesInDataset)
					{
						resultsOutputWriter.write(Double.toString(confusionMatrix.get(s).get("TruePositive")));
						resultsOutputWriter.write("\t");
						resultsOutputWriter.write(Double.toString(confusionMatrix.get(s).get("FalsePositive")));
						resultsOutputWriter.write("\t");
					}
					resultsOutputWriter.newLine();
					resultsOutputWriter.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					System.exit(0);
				}

				classWeightIndexMapping = updateWeightIndices(classWeightIndexMapping, finalWeightIndexMapping, orderedClassesWithVaryingWeights);
			}
		}
	}

	/**
	 * Calculate statistics from a confusion matrix.
	 * 
	 * Supplying a test dataset in addition to the input (training) dataset should only be used when the predictions from the input dataset have been combined
	 * with the predictions from the input dataset.
	 * 
	 * @param confusionMatrix - The confusion matrix for the predictions from which the statistics should be calculated.
	 * @param numberOfForestsToCreate - The number of forests created.
	 * @param inputFile - The location of the input (training) dataset.
	 * @param testSetLocation - The file containing the test set used.
	 * @return - A map containing the names of the statistics calculated along with their values.
	 */
	public static Map<String, Double> calculateStats(Map<String, Map<String, Double>> confusionMatrix, int numberOfForestsToCreate, String inputFile)
	{
		return calculateStats(confusionMatrix, numberOfForestsToCreate, inputFile, null);
	}

	public static Map<String, Double> calculateStats(Map<String, Map<String, Double>> confusionMatrix, int numberOfForestsToCreate, String inputFile,
			String testSetLocation)
	{
		// Process the input dataset, and determine the number of observations from each class in the dataset.
		ProcessDataForGrowing processedInputFile = new ProcessDataForGrowing(inputFile, new TreeGrowthControl());
		Map<String, Integer> countsOfClass = new HashMap<String, Integer>();
		for (String s : new HashSet<String>(processedInputFile.responseData))
		{
			countsOfClass.put(s, Collections.frequency(processedInputFile.responseData, s));
		}

		// Process the test set dataset (if one was supplied), and add the counts of the classes in the test set to the counts in the input dataset.
		if (testSetLocation != null)
		{
			ProcessDataForGrowing processedTestSet = new ProcessDataForGrowing(testSetLocation, new TreeGrowthControl());
			for (String s : countsOfClass.keySet())
			{
				Integer trainingSetCounts = countsOfClass.get(s);
				countsOfClass.put(s, Collections.frequency(processedTestSet.responseData, s) + trainingSetCounts);
			}
		}

		double totalPredictions = 0.0;  // The total number of predictions made.
		double incorrectPredictions = 0.0;  // The total number of incorrect predictions made.
		// Macro rather than micro calculations are used in order to handle datasets with imbalanced classes.
		double macroRecall = 0.0;
		double macroPrecision = 0.0;
		double macroGMean = 1.0;
		double MCC = 0.0;
		for (String s : confusionMatrix.keySet())
		{
			double TP = confusionMatrix.get(s).get("TruePositive");  // Observations of this class predicted as this class.
			double FP = confusionMatrix.get(s).get("FalsePositive");  // Observations from other classes predicted as this class.
			// For binary classification the false negatives are easy to determine. However, for multi-class problems the false negatives must be
			// calculated as the difference between the number of predictions of observations in a class and the number of true positives for the class.
			// In this case the number of predictions of the observations in a class is the number of observations in the class multiplied by the number
			// of forest created (as each observation is predicted once per forest). This assumes that the number of trees in the forest is sufficient
			// for each observation to be oob on a sufficient number of trees (or at the very least one tree).
    		double FN = (numberOfForestsToCreate * countsOfClass.get(s)) - TP;  // Observations of this class predicted as other classes.
    		double recall = (TP / (TP + FN));
    		macroRecall += recall;
    		double precision = (TP / (TP + FP));
    		macroPrecision += precision;
    		macroGMean *= recall;
    		totalPredictions += TP + FP;
    		incorrectPredictions += FP;
		}
		if (confusionMatrix.size() == 2)
		{
			// If there are only two classes, then calculate the MCC. Will handle classes of any name.
			List<Double> correctPredictionsMCC = new ArrayList<Double>();
			List<Double> incorrectPredictionsMCC = new ArrayList<Double>();
			for (String s : confusionMatrix.keySet())
			{
				correctPredictionsMCC.add(confusionMatrix.get(s).get("TruePositive"));
				incorrectPredictionsMCC.add(confusionMatrix.get(s).get("FalsePositive"));
			}
			// It doesn't matter which class is 'positive' and which 'negative' in the MCC calculation.
			double TP = correctPredictionsMCC.get(0);
			double FP = incorrectPredictionsMCC.get(0);
			double TN = correctPredictionsMCC.get(1);
			double FN = incorrectPredictionsMCC.get(1);
			MCC = ((TP * TN) - (FP * FN)) / (Math.sqrt((TP + TN) * (TP + FN) * (TN + FP) * (TN + FN)));
		}
		macroRecall /= confusionMatrix.size();
		macroPrecision /= confusionMatrix.size();
		double fHalf = (1 + (0.5 * 0.5)) * ((macroPrecision * macroRecall) / ((0.5 * 0.5 * macroPrecision) + macroRecall));
		double fOne = 2 * ((macroPrecision * macroRecall) / (macroPrecision + macroRecall));
		double fTwo = (1 + (2 * 2)) * ((macroPrecision * macroRecall) / ((2 * 2 * macroPrecision) + macroRecall));
		double gMean = Math.pow(macroGMean, (1.0 / confusionMatrix.size()));
		double errorRate = incorrectPredictions / totalPredictions;

		// Generate the return map.
		Map<String, Double> statistics = new HashMap<String, Double>();
		statistics.put("MCC", MCC);
		statistics.put("F0.5", fHalf);
		statistics.put("F1", fOne);
		statistics.put("F2", fTwo);
		statistics.put("GMean", gMean);
		statistics.put("ErrorRate", errorRate);
		return statistics;
	}

	/**
	 * Calculate the confusion matrix for a given set of parameters.
	 * 
	 * Generates numberOfForestsToCreate forests, and combines the predictions of the observations over all the forests to produce the final
	 * confusion matrix,
	 * 
	 * @param weights - The mapping of the weights for each class in the input dataset.
	 * @param ctrl - The control object used to parse the input dataset and grow the forests.
	 * @param inputFile - The location of the input dataset.
	 * @param seeds - The seeds that will be used to grow the forests (one for every forest being grown).
	 * @param numberOfForestsToCreate - The number of forests to create.
	 * @param testFileLocation - The location of the test dataset (or null if there isn't one).
	 * @return - A mapping from "Training" to the OOB confusion matix and "Testing" to the confusion matrix for the test dataset, along with the average time
	 * 			 taken to generate a forest.
	 */
	public static ImmutableTwoValues<Map<String, Map<String, Map<String, Double>>>, Long> generateForestsNoCV(Map<String, Double> weights, TreeGrowthControl ctrl, String inputFile, List<Long> seeds,
			int numberOfForestsToCreate, String testFileLocation)
	{
		// Setup the timing variables.
		Date startTime;
		Date endTime;
		long timeTaken = 0l;

		// Process the input dataset (and test dataset if there is one).
		ProcessDataForGrowing processedInputFile = new ProcessDataForGrowing(inputFile, ctrl);
		ProcessDataForGrowing processedTestFile = null;
		if (testFileLocation != null)
		{
			processedTestFile = new ProcessDataForGrowing(testFileLocation, new TreeGrowthControl());
		}

		// Create the confusion matrices for the input dataset (and test dataset if there is one), and determine the counts of each class in each dataset.
		Map<String, Map<String, Double>> confusionMatrix = new HashMap<String, Map<String, Double>>();
		Map<String, Map<String, Double>> testConfusionMatrix = new HashMap<String, Map<String, Double>>();
		for (String s : weights.keySet())
		{
			confusionMatrix.put(s, new HashMap<String, Double>());
			confusionMatrix.get(s).put("TruePositive", 0.0);
			confusionMatrix.get(s).put("FalsePositive", 0.0);
			testConfusionMatrix.put(s, new HashMap<String, Double>());
			testConfusionMatrix.get(s).put("TruePositive", 0.0);
			testConfusionMatrix.get(s).put("FalsePositive", 0.0);
		}

		// Generate each forest, and determine the additions ot amke to the confusion matrix.
		for (int i = 0; i < numberOfForestsToCreate; i++)
		{
			// Get the seed for this forest.
			long seed = seeds.get(i);

			startTime = new Date();
			Forest forest = new Forest(processedInputFile, ctrl, seed);
			forest.setWeightsByClass(weights);
			forest.growForest();
			for (String s : forest.oobConfusionMatrix.keySet())
    		{
    			Double oldTruePos = confusionMatrix.get(s).get("TruePositive");
    			Double newTruePos = oldTruePos + forest.oobConfusionMatrix.get(s).get("TruePositive");
    			confusionMatrix.get(s).put("TruePositive", newTruePos);
    			Double oldFalsePos = confusionMatrix.get(s).get("FalsePositive");
    			Double newFalsePos = oldFalsePos + forest.oobConfusionMatrix.get(s).get("FalsePositive");
    			confusionMatrix.get(s).put("FalsePositive", newFalsePos);
    		}
			endTime = new Date();
			timeTaken += endTime.getTime() - startTime.getTime();

			// Generate the test set predictions.
			if (testFileLocation != null)
			{
				Map<String, Map<String, Double>> predResults = forest.predict(processedTestFile).second;
				for (String s : predResults.keySet())
	    		{
	    			Double oldTruePos = testConfusionMatrix.get(s).get("TruePositive");
	    			Double newTruePos = oldTruePos + predResults.get(s).get("TruePositive");
	    			testConfusionMatrix.get(s).put("TruePositive", newTruePos);
	    			Double oldFalsePos = testConfusionMatrix.get(s).get("FalsePositive");
	    			Double newFalsePos = oldFalsePos + predResults.get(s).get("FalsePositive");
	    			testConfusionMatrix.get(s).put("FalsePositive", newFalsePos);
	    		}
			}
		}
		timeTaken /= (double) numberOfForestsToCreate;

		// Generate the return value.
		Map<String, Map<String, Map<String, Double>>> confusionMatReturn = new HashMap<String, Map<String, Map<String, Double>>>();
		confusionMatReturn.put("Training", confusionMatrix);
		confusionMatReturn.put("Testing", testConfusionMatrix);
		return new ImmutableTwoValues<Map<String, Map<String, Map<String, Double>>>, Long>(confusionMatReturn, timeTaken);
	}


	/**
	 * Calculate the confusion matrix for a given set of parameters.
	 * 
	 * Performs numberOfForestsToCreate rounds of cross validation with cvFolds folds. The predictions from each round are combined into one confusion matrix.
	 * 
	 * @param weights - The mapping of the weights for each class in the input dataset.
	 * @param ctrl - The control object used to parse the input dataset and grow the forests.
	 * @param cvFoldLocation - The location of the directory containing the cross validation folds.
	 * @param inputFile - The location of the input dataset.
	 * @param seeds - The seeds that will be used to grow the forests (one for every forest being grown).
	 * @param numberOfForestsToCreate - The number of forests to create.
	 * @param cvFolds - The number of cross validation folds used.
	 * @return - The confusion matrix of the predictions along with the average time taken to train and test through a full set of cross validation folds.
	 */
	public static ImmutableTwoValues<Map<String, Map<String, Double>>, Long> generateForestsWithCV(Map<String, Double> weights, TreeGrowthControl ctrl, String cvFoldLocation, String inputFile,
			List<Long> seeds, int numberOfForestsToCreate, int cvFolds)
	{
		// Setup the timing variables.
		Date startTime;
		Date endTime;
		long timeTaken = 0l;

		// Create the confusion matrices for the input dataset.
		Map<String, Map<String, Double>> confusionMatrix = new HashMap<String, Map<String, Double>>();
		for (String s : weights.keySet())
		{
			confusionMatrix.put(s, new HashMap<String, Double>());
			confusionMatrix.get(s).put("TruePositive", 0.0);
			confusionMatrix.get(s).put("FalsePositive", 0.0);
		}

		// Perform the cross validation for each of the desired number of repetitions.
		for (int i = 0; i < numberOfForestsToCreate; i++)
		{
			// Get the seed for this forest.
			long seed = seeds.get(i);

			String currentCVFoldLocation = cvFoldLocation + Integer.toString(i);
			startTime = new Date();
			Forest forest;
			for (int j = 0; j < cvFolds; j++)
			{
				String trainingSet = currentCVFoldLocation + "/" + Integer.toString(j) + "/Train.txt";
				String testingSet = currentCVFoldLocation + "/" + Integer.toString(j) + "/Test.txt";
				forest = new Forest(trainingSet, ctrl, seed);
				forest.setWeightsByClass(weights);
				forest.growForest();
				Map<String, Map<String, Double>> confMatrix = forest.predict(new ProcessDataForGrowing(testingSet, new TreeGrowthControl())).second;
				for (String s : confMatrix.keySet())
	    		{
	    			Double oldTruePos = confusionMatrix.get(s).get("TruePositive");
	    			Double newTruePos = oldTruePos + confMatrix.get(s).get("TruePositive");
	    			confusionMatrix.get(s).put("TruePositive", newTruePos);
	    			Double oldFalsePos = confusionMatrix.get(s).get("FalsePositive");
	    			Double newFalsePos = oldFalsePos + confMatrix.get(s).get("FalsePositive");
	    			confusionMatrix.get(s).put("FalsePositive", newFalsePos);
	    		}
			}
			endTime = new Date();
    		timeTaken += endTime.getTime() - startTime.getTime();
		}
		timeTaken /= (double) numberOfForestsToCreate;

		return new ImmutableTwoValues<Map<String, Map<String, Double>>, Long>(confusionMatrix, timeTaken);
	}

	/**
	 * Checks whether all indices are equal to 0.
	 * 
	 * @param classWeightIndexMapping - Maps each class s to the index of the weight in varyingClassWeightMapping.get(s) currently assigned to it.
	 * @return - true if all weight combinations have been explored, else false.
	 */
	public static boolean terminationReached(Map<String, Integer> classWeightIndexMapping)
	{
		boolean isTerminate = true;
		for (String s : classWeightIndexMapping.keySet())
		{
			// Terminate only if all indices are 0.
			isTerminate = isTerminate && (classWeightIndexMapping.get(s) == 0);
		}
		return isTerminate;
	}

	/**
	 * Updates the mapping from the classes to the indices into their weight vectors.
	 * 
	 * Increments the indices as a binary register would increment itself. If you have three classes ordered A, B, C such that A has 3 possible weights,
	 * B has 2 possible weights and C has 4 possible weights, then a few example updates of the indices are as follows:
	 * 		start : A->2 (max 2)	A->0	A->2	A->2
	 * 				B->0 (max 1)	B->0	B->1	B->1
	 * 				C->1 (max 3)	C->0	C->0	C->3
	 * 
	 * 		end :	A->0			A->1	A->0	A->0
	 * 				B->1			B->0	B->0	B->0
	 * 				C->1			C->0	C->1	C->0
	 * The last update (A,B,C going from 2,1,3 to 0,0,0) indicates that all the possible index combinations have been used, and that the termination condition
	 * will be satisfied.
	 * 
	 * @param classWeightIndexMapping - Maps each class s to the index of the weight in varyingClassWeightMapping.get(s) currently assigned to it.
	 * @param finalWeightIndexMapping - Maps each class to the maximum possible index it can take.
	 * @return - A mapping from the classes to the indices into their weight vectors.
	 */
	public static Map<String, Integer> updateWeightIndices(Map<String, Integer> classWeightIndexMapping, Map<String, Integer> finalWeightIndexMapping,
			List<String> orderedClassesWithVaryingWeights)
	{
		for (String s : orderedClassesWithVaryingWeights)
		{
			if (classWeightIndexMapping.get(s) == finalWeightIndexMapping.get(s))
			{
				// If the 
				classWeightIndexMapping.put(s, 0);
			}
			else
			{
				classWeightIndexMapping.put(s, classWeightIndexMapping.get(s) + 1);
				break;
			}
		}
		return  classWeightIndexMapping;
	}

}