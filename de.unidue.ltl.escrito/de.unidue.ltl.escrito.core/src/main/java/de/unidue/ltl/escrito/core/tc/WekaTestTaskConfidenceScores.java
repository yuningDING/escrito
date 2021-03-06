package de.unidue.ltl.escrito.core.tc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import meka.classifiers.multilabel.MultiLabelClassifier;
import meka.core.MLUtils;
import meka.core.Result;
import meka.core.ThresholdUtils;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.dkpro.lab.engine.TaskContext;
import org.dkpro.lab.storage.StorageService.AccessMode;
import org.dkpro.tc.core.Constants;
import org.dkpro.tc.ml.weka.core.MekaTrainer;
import org.dkpro.tc.ml.weka.core.WekaTrainer;
import org.dkpro.tc.ml.weka.core._eka;
import org.dkpro.tc.ml.weka.task.WekaFeatureSelector;
import org.dkpro.tc.ml.weka.task.WekaOutcomeHarmonizer;
import org.dkpro.tc.ml.weka.task.WekaTestTask;
import org.dkpro.tc.ml.weka.util.MultilabelResult;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.converters.ConverterUtils.DataSink;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;

public class WekaTestTaskConfidenceScores
	extends WekaTestTask
{


	@Override
	public void execute(TaskContext aContext) throws Exception
	{
		boolean multiLabel = learningMode.equals(Constants.LM_MULTI_LABEL);

		File arffFileTrain = getFile(aContext, TEST_TASK_INPUT_KEY_TRAINING_DATA,
				Constants.FILENAME_DATA_IN_CLASSIFIER_FORMAT, AccessMode.READONLY);
		File arffFileTest = getFile(aContext, TEST_TASK_INPUT_KEY_TEST_DATA,
				Constants.FILENAME_DATA_IN_CLASSIFIER_FORMAT, AccessMode.READONLY);

		Instances trainData = _eka.getInstances(arffFileTrain, multiLabel);
		Instances testData = _eka.getInstances(arffFileTest, multiLabel);

		WekaOutcomeHarmonizer harmonizer = new WekaOutcomeHarmonizer(trainData, testData,
				learningMode);
		testData = harmonizer.harmonize();

		Instances copyTestData = new Instances(testData);
		trainData = _eka.removeInstanceId(trainData, multiLabel);
		testData = _eka.removeInstanceId(testData, multiLabel);

		WekaFeatureSelector selector = new WekaFeatureSelector(trainData, testData, multiLabel,
				attributeEvaluator, featureSearcher, labelTransformationMethod, numLabelsToKeep,
				aContext.getFile("featureSelection", AccessMode.READWRITE));
		selector.apply();
		trainData = selector.getTrainingInstances();
		testData = selector.getTestingInstances();

		// file to hold prediction results
		File evalOutput = getFile(aContext, "", evaluationBin, AccessMode.READWRITE);
		File model = aContext.getFile(MODEL_CLASSIFIER, AccessMode.READWRITE);
		// evaluation & prediction generation
		if (multiLabel) {
			// we don't need to build the classifier - meka does this
			// internally

			MekaTrainer trainer = new MekaTrainer(false);
			Classifier cl = trainer.train(trainData, model, getParameters(classificationArguments));

			Result r = getEvaluationMultilabel(cl, trainData, testData, threshold);
			writeMlResultToFile(
					new MultilabelResult(r.allTrueValues(), r.allPredictions(), threshold),
					evalOutput);
			testData = getPredictionInstancesMultiLabel(testData, cl,
					getMekaThreshold(threshold, r, trainData));
			testData = _eka.addInstanceId(testData, copyTestData, true);
		}
		else {

			WekaTrainer trainer = new WekaTrainer();
			Classifier classifier = trainer.train(trainData, model,
					getParameters(classificationArguments));

			createWekaEvaluationObject(classifier, evalOutput, trainData, testData);
			Instances testDataOrig = new Instances(testData);
			testData = getPredictionInstancesSingleLabel(testData, classifier);
			testData = _eka.addInstanceId(testData, copyTestData, false);



			File confScores = aContext.getFile("id2ConfScore.txt", AccessMode.READWRITE);
			BufferedWriter bw = new BufferedWriter(new FileWriter(confScores));
			
			for (int i = 0; i<testDataOrig.size(); i++){
			//	System.out.println(copyTestData.get(i).value(copyTestData.attribute("DKProTCInstanceID")));
				bw.write(copyTestData.get(i).value(copyTestData.attribute("DKProTCInstanceID"))+"\t");
				double[] classifierDistribution = classifier.distributionForInstance(testDataOrig.get(i));
				double max = 0.0;
				for (double d : classifierDistribution){
				//	System.out.print(d+" ");
					max = Math.max(max, d);
				}
				bw.write(max+"\n");
			//	System.out.println();
			}
			bw.close();

		}



		// Write out the prediction - the data sink expects an .arff ending file so we game it a bit
		// and rename the file afterwards to .txt
		File predictionFile = getFile(aContext, "", Constants.FILENAME_PREDICTIONS,
				AccessMode.READWRITE);
		File arffDummy = new File(predictionFile.getParent(), "prediction.arff");
		DataSink.write(arffDummy.getAbsolutePath(), testData);
		FileUtils.moveFile(arffDummy, predictionFile);
	}


	protected void createWekaEvaluationObject(Classifier classifier, File evalOutput,
			Instances trainData, Instances testData)
					throws Exception
	{
		Evaluation eval = new Evaluation(trainData);
		eval.evaluateModel(classifier, testData);

		weka.core.SerializationHelper.write(evalOutput.getAbsolutePath(), eval);

	}

	protected List<String> getParameters(List<Object> classificationArguments)
	{
		List<String> o = new ArrayList<>();

		for (int i = 1; i < classificationArguments.size(); i++) {
			o.add((String) classificationArguments.get(i));
		}

		return o;
	}

	public Instances getPredictionInstancesSingleLabel(Instances testData, Classifier cl)
			throws Exception
	{

		StringBuffer classVals = new StringBuffer();
		for (int i = 0; i < testData.classAttribute().numValues(); i++) {
			if (classVals.length() > 0) {
				classVals.append(",");
			}
			classVals.append(testData.classAttribute().value(i));
		}

		// get predictions
		List<Double> labelPredictionList = new ArrayList<Double>();
		for (int i = 0; i < testData.size(); i++) {
			labelPredictionList.add(cl.classifyInstance(testData.instance(i)));
		}

		// add an attribute with the predicted values at the end off the attributes
		Add filter = new Add();
		filter.setAttributeName(WekaTestTask.PREDICTION_CLASS_LABEL_NAME);
		if (classVals.length() > 0) {
			filter.setAttributeType(new SelectedTag(Attribute.NOMINAL, Add.TAGS_TYPE));
			filter.setNominalLabels(classVals.toString());
		}
		filter.setInputFormat(testData);
		testData = Filter.useFilter(testData, filter);

		// fill predicted values for each instance
		for (int i = 0; i < labelPredictionList.size(); i++) {
			testData.instance(i).setValue(testData.classIndex() + 1, labelPredictionList.get(i));
		}
		return testData;
	}

	protected Result getEvaluationMultilabel(Classifier cl, Instances trainData, Instances testData,
			String threshold)
					throws Exception
	{
		Result r = meka.classifiers.multilabel.Evaluation.evaluateModel((MultiLabelClassifier) cl,
				trainData, testData, threshold);
		return r;
	}

	protected void writeMlResultToFile(MultilabelResult result, File file)
			throws FileNotFoundException, IOException
	{
		ObjectOutputStream stream = null;
		try {
			stream = new ObjectOutputStream(new FileOutputStream(file));
			stream.writeObject(result);
		}
		finally {
			IOUtils.closeQuietly(stream);
		}
	}

	private Instances getPredictionInstancesMultiLabel(Instances testData, Classifier cl,
			double[] thresholdArray)
					throws Exception
	{
		int numLabels = testData.classIndex();

		// get predictions
		List<double[]> labelPredictionList = new ArrayList<double[]>();
		for (int i = 0; i < testData.numInstances(); i++) {
			labelPredictionList.add(cl.distributionForInstance(testData.instance(i)));
		}

		// add attributes to store predictions in test data
		Add filter = new Add();
		for (int i = 0; i < numLabels; i++) {
			filter.setAttributeIndex(Integer.toString(numLabels + i + 1));
			filter.setNominalLabels("0,1");
			filter.setAttributeName(
					testData.attribute(i).name() + "_" + WekaTestTask.PREDICTION_CLASS_LABEL_NAME);
			filter.setInputFormat(testData);
			testData = Filter.useFilter(testData, filter);
		}

		// fill predicted values for each instance
		for (int i = 0; i < labelPredictionList.size(); i++) {
			for (int j = 0; j < labelPredictionList.get(i).length; j++) {
				testData.instance(i).setValue(j + numLabels,
						labelPredictionList.get(i)[j] >= thresholdArray[j] ? 1. : 0.);
			}
		}
		return testData;
	}

	/**
	 * Calculates the threshold to turn a ranking of label predictions into a bipartition (one
	 * threshold for each label)
	 *
	 * @param threshold
	 *            PCut1, PCutL, or number between 0 and 1 (see Meka documentation for details on
	 *            this)
	 * @param r
	 *            Results file
	 * @param data
	 *            training data to use for automatically determining the threshold
	 * @return an array with thresholds for each label
	 * @throws Exception
	 *             an exception
	 */
	protected double[] getMekaThreshold(String threshold, Result r, Instances data) throws Exception
	{
		double[] t = new double[r.L];
		if (threshold.equals("PCut1")) {
			// one threshold for all labels (PCut1 in Meka)
			Arrays.fill(t, ThresholdUtils.calibrateThreshold(r.predictions,
					(Double) r.getValue("LCard_train")));
		}
		else if (threshold.equals("PCutL")) {
			// one threshold for each label (PCutL in Meka)
			t = ThresholdUtils.calibrateThresholds(r.predictions, MLUtils.labelCardinalities(data));
			throw new Exception("Not yet implemented.");
		}
		else {
			// manual threshold
			Arrays.fill(t, Double.valueOf(threshold));
		}
		return t;
	}

	public static File getFile(TaskContext aContext, String key, String entry, AccessMode mode)
	{
		String path = aContext.getFolder(key, mode).getPath();
		String pathToArff = path + "/" + entry;

		return new File(pathToArff);
	}





}
