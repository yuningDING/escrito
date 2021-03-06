package de.unidue.ltl.escrito.io.shortanswer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.dkpro.tc.api.type.TextClassificationOutcome;
import org.dkpro.tc.api.type.TextClassificationTarget;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ResourceUtils;
import de.unidue.ltl.escrito.core.types.LearnerAnswerWithReferenceAnswer;
import de.unidue.ltl.escrito.io.util.Utils;

public class CregReader extends JCasCollectionReader_ImplBase {

	protected static final String DEFAULT_LANGUAGE = "de";

	public static final String PARAM_INPUT_FILE = "InputFile";
	@ConfigurationParameter(name = PARAM_INPUT_FILE, mandatory = true)
	protected String inputFileString;
	protected URL  inputURL;

	public static final String PARAM_CORPUSNAME = "corpusName";
	@ConfigurationParameter(name = PARAM_CORPUSNAME, mandatory = false, defaultValue = "CREG")
	protected String corpusName;

	public static final String PARAM_LANGUAGE = "Language";
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false, defaultValue = DEFAULT_LANGUAGE)
	protected String language;

	public static final String PARAM_ENCODING = "Encoding";
	@ConfigurationParameter(name = PARAM_ENCODING, mandatory = false, defaultValue = "UTF-8")
	private String encoding;

	public static final String PARAM_PROMPT_SET_ID = "PromptSetId";
	@ConfigurationParameter(name = PARAM_PROMPT_SET_ID, mandatory = false)
	protected String requestedPromptSetId; 

	public static final String PARAM_QUESTION_PREFIX = "QuestionPrefix";
	@ConfigurationParameter(name = PARAM_QUESTION_PREFIX, mandatory = true)
	private String questionPrefix;

	public static final String PARAM_TARGET_ANSWER_PREFIX = "TargetAnswerPrefix";
	@ConfigurationParameter(name = PARAM_TARGET_ANSWER_PREFIX, mandatory = true)
	private String targetAnswerPrefix;

	public static final String PARAM_PREPROCESSING_OF_CONNECTED_TEXTS = "preproTexts";
	@ConfigurationParameter(name = PARAM_PREPROCESSING_OF_CONNECTED_TEXTS, mandatory = false, defaultValue="true")
	protected boolean preproTexts;

	protected int currentIndex;    

	protected Queue<CregItem> items;
	private Map<String, String> targetAnswers;
	private Map<String, String> questions;

	private List<Integer> grades;
	
	
	public static final String[] PromptIds_OSU  = new String[] {"OSU_82", "OSU_83", "OSU_40", "OSU_84", "OSU_85", "OSU_42", "OSU_86", 
			"OSU_43", "OSU_87", "OSU_44", "OSU_88", "OSU_45", "OSU_89", "OSU_80", "OSU_81", "OSU_46", "OSU_48", "OSU_50", "OSU_51",
			"OSU_52", "OSU_53", "OSU_10", "OSU_54", "OSU_11", "OSU_55", "OSU_12", "OSU_13", "OSU_15", "OSU_16", "OSU_17", "OSU_62",
			"OSU_63", "OSU_64", "OSU_65", "OSU_66", "OSU_68", "OSU_69", "OSU_71", "OSU_72", "OSU_73", "OSU_1", "OSU_74", "OSU_2", 
			"OSU_75", "OSU_3", "OSU_76", "OSU_4", "OSU_77", "OSU_5", "OSU_78", "OSU_6", "OSU_7", "OSU_8", "OSU_9", "OSU_70", "OSU_79",
			"OSU_36", "OSU_37", "OSU_38", "OSU_39"};
    public static final String[] PromptIds_KU  = new String[] {"KU_244", "KU_243", "KU_240", "KU_120", "KU_241", "KU_119", "KU_116",
    		"KU_237", "KU_115", "KU_236", "KU_118", "KU_239", "KU_117", "KU_238", "KU_136", "KU_378", "KU_135", "KU_377", "KU_90",
    		"KU_101", "KU_189", "KU_100", "KU_188", "KU_103", "KU_102", "KU_187", "KU_98", "KU_99", "KU_191", "KU_194", "KU_190", 
    		"KU_112", "KU_233", "KU_232", "KU_235", "KU_234", "KU_110", "KU_231", "KU_230", "KU_161", "KU_160", "KU_71", "KU_167",
    		"KU_166", "KU_169", "KU_168", "KU_163", "KU_162", "KU_165", "KU_164", "KU_159", "KU_72", "KU_73", "KU_74", "KU_75", 
    		"KU_77", "KU_78", "KU_79", "KU_170", "KU_171", "KU_80", "KU_81", "KU_82", "KU_299", "KU_298", "KU_331", "KU_334", "KU_333",
    		"KU_297", "KU_328", "KU_325", "KU_324", "KU_327", "KU_326", "KU_84", "KU_85", "KU_86", "KU_87", "KU_88", "KU_89", "KU_381",
    		"KU_380", "KU_145", "KU_387", "KU_144", "KU_386", "KU_147", "KU_301", "KU_389", "KU_146", "KU_300", "KU_388", "KU_141", "KU_383", 
    		"KU_140", "KU_382", "KU_143", "KU_385", "KU_142", "KU_384", "KU_138", "KU_137", "KU_379", "KU_139", "KU_390", "KU_150", "KU_155",
    		"KU_158", "KU_157", "KU_152", "KU_151", "KU_154", "KU_153", "KU_307", "KU_306", "KU_149", "KU_148"};

	
	@Override
	public void initialize(UimaContext aContext)
			throws ResourceInitializationException
	{
		items = new LinkedList<CregItem>();

		grades = new ArrayList<Integer>();
		targetAnswers = new HashMap<String, String>();
		questions = new HashMap<String, String>();

		try {
			inputURL = ResourceUtils.resolveLocation(inputFileString, this, aContext);
			// if the input is a directory, read all xml files form the directory
			if (new File ( inputURL.getFile()).isDirectory()){
				File[] fileArray = new File( inputURL.getFile()).listFiles(
						new FilenameFilter(){  
							public boolean accept(File dir, String name){  
								return name.indexOf(".xml")!=-1;
							}  
						}
						);
				//	System.out.println(Arrays.toString(fileArray));
				for (File file : fileArray){
					//	System.out.println(file.getPath());
					URL fileURL = ResourceUtils.resolveLocation(file.getPath(), this, aContext);
					extractLearnerAnswersFromFile(fileURL);
				}
			} else {
				extractLearnerAnswersFromFile(inputURL);
			}
		}
		catch (Exception e) {
			throw new ResourceInitializationException(e);
		}

		Gson gson = new Gson();
		Type listType = new TypeToken<List<Integer>>() {}.getType();

		try {
			FileUtils.writeStringToFile(new File("target/scores.txt"), gson.toJson(grades, listType));
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
		currentIndex = 0;	
		if (preproTexts){
			Utils.preprocessConnectedTexts(targetAnswers, corpusName, targetAnswerPrefix, "de");
			Utils.preprocessConnectedTexts(questions, corpusName, questionPrefix, "de");
		}
	}

	private void extractLearnerAnswersFromFile(URL fileURL) throws JDOMException, IOException {
		String prefix = "KU_";
		if (fileURL.getFile().contains("OSU")){
			prefix = "OSU_";
		}
		Document doc = new SAXBuilder().build(fileURL);
		Element corpus = doc.getRootElement();
		Element qNode = corpus.getChild("Questions");
		List<Element> questionNodes = qNode.getChildren("Question");
		for (Element question: questionNodes){
			String qid = prefix+question.getAttributeValue("id");
			String questionText = question.getChild("questionString").getText().trim();
			questions.put(qid, Utils.cleanString(questionText));
			//	System.out.println("Question "+qid+": "+questionText);
			Element taNode = question.getChild("TargetAnswers");
			List<Element> targetAnswerNodes = taNode.getChildren("TargetAnswer");
			String firstTargetAnswer = null;
			String firstTargetAnswerId = null;
			boolean foundFirst = false;
			for (Element targetAnswer : targetAnswerNodes){
				String taid = prefix+targetAnswer.getAttributeValue("id");
				//Some target answers have no text
				if (!(targetAnswer.getChildren().isEmpty())){
					String taText = targetAnswer.getChild("answerText").getText().trim();
					targetAnswers.put(taid, Utils.cleanString(taText));
					//		System.out.println("TA "+taid+": "+taText);
					if (!(foundFirst)){
						firstTargetAnswer = Utils.cleanString(taText);
						firstTargetAnswerId = taid;
						foundFirst = true;
					}
				}
			}
			Element laNode = question.getChild("StudentAnswers");
			List<Element> studentAnswers = laNode.getChildren("StudentAnswer");
			for (Element studentAnswer : studentAnswers){
				String id = prefix+studentAnswer.getAttributeValue("id");
				String studentId = prefix+studentAnswer.getAttributeValue("student_id");
				List<Element> diagnoses = studentAnswer.getChildren("diagnosis");
				if (diagnoses.size() != 2){
					System.err.println("Found "+diagnoses.size()+" diagnoses");
				}
				String answertext1 = diagnoses.get(0).getChild("answerText").getText().trim();
				String binary1 = diagnoses.get(0).getAttributeValue("binary");
				String detailed1 = diagnoses.get(0).getAttributeValue("detailed");
				String teacherId1 = prefix+diagnoses.get(0).getAttributeValue("teacher_id");
				String diagnosisId1 = prefix+diagnoses.get(0).getAttributeValue("id");
				String answertext2 = diagnoses.get(1).getChild("answerText").getText().trim();
				String binary2 = diagnoses.get(1).getAttributeValue("binary");
				String detailed2 = diagnoses.get(1).getAttributeValue("detailed");
				String teacherId2 = prefix+diagnoses.get(1).getAttributeValue("teacher_id");
				String diagnosisId2 = prefix+diagnoses.get(1).getAttributeValue("id");
				String closestTaId1 = diagnoses.get(0).getAttributeValue("closestTA_id");
				String closestTaId2 = diagnoses.get(1).getAttributeValue("closestTA_id");
				//		System.out.println("StudentAnswer "+id+" "+studentId+" "+binary1+"/"+binary2+" "+detailed1+"/"+detailed2+" "+answertext1);
				int score = -1;
				if (binary1.equals("false")){
					score = 0;
				} else if (binary1.equals("true")){
					score = 1;
				} else {
					System.err.println("Illegal binary value "+binary1);
				}
				// TODO, do this in a more relaxed way
				answertext1 = Utils.cleanString(answertext1);
				String closestTa = targetAnswers.get(closestTaId1);
				String closestTaId = closestTaId1;
				if (closestTa == null){
				//	System.err.println("Empy closest target answer. We take the first TA");
					closestTa = firstTargetAnswer;
					closestTaId = firstTargetAnswerId;
				}
				CregItem item = new CregItem(studentId, qid, answertext1, score, closestTa);
				item.setTargetAnswerId(closestTaId);
				//		System.out.println("TA id: "+closestTaId);
				item.setBinary1(binary1);
				item.setBinary2(binary2);
				item.setDetailed1(detailed1);
				item.setDetailed2(detailed2);
				item.setTeacherId1(teacherId1);
				item.setTeacherId2(teacherId2);
				item.setAnswerText1(answertext1);
				item.setAnswerText2(answertext2);
				item.setDiagnosisId1(diagnosisId1);
				item.setDiagnosisId2(diagnosisId2);
				if (requestedPromptSetId != null && !requestedPromptSetId.equals(qid)) {
					break;
				} else {
				items.add(item);
				}
			}   
		}
	}

	@Override
	public boolean hasNext()
			throws IOException 
	{
		return !items.isEmpty();
	}

	@Override
	public void getNext(JCas jcas)
			throws IOException, CollectionException
	{
		CregItem item = items.poll();
		getLogger().debug(item);
		String itemId = item.getQuestionId()+"_"+item.getStudentId(); 

		try
		{
			if (language != null) {
				jcas.setDocumentLanguage(language);
			}
			else {
				jcas.setDocumentLanguage(DEFAULT_LANGUAGE);
			}

			jcas.setDocumentText(item.getText());

			// TODO: student ID is not unique, we should use a combination of studentId and questionId
			DocumentMetaData dmd = DocumentMetaData.create(jcas);
			dmd.setDocumentId(itemId); 
			dmd.setDocumentTitle(itemId);
			dmd.setDocumentUri( inputURL.toURI().toString());
			dmd.setCollectionId(itemId);

		} 
		catch (URISyntaxException e) {
			throw new CollectionException(e);
		}
		LearnerAnswerWithReferenceAnswer learnerAnswer = new LearnerAnswerWithReferenceAnswer(jcas, 0, jcas.getDocumentText().length());
		learnerAnswer.setPromptId(item.getQuestionId());
		StringArray ids = new StringArray(jcas, 1);
		ids.set(0, item.getClosestTaId1());
		learnerAnswer.setReferenceAnswerIds(ids);
		learnerAnswer.addToIndexes();
		TextClassificationTarget unit = new TextClassificationTarget(jcas, 0, jcas.getDocumentText().length());
		// will add the token content as a suffix to the ID of this unit 
		//	System.out.println("ItemId: "+itemId);
		unit.setSuffix(itemId);
		unit.addToIndexes();

		TextClassificationOutcome outcome = new TextClassificationOutcome(jcas, 0, jcas.getDocumentText().length());
		outcome.setOutcome(String.valueOf(item.getGrade()));	
		outcome.addToIndexes();

		currentIndex++;
	}



	@Override
	public Progress[] getProgress()
	{
		return new Progress[] { new ProgressImpl(currentIndex, grades.size(), Progress.ENTITIES) };
	}

}

class CregItem extends ItemWithTargetAnswer {


	public String getAnswerText1() {
		return answerText1;
	}

	public void setAnswerText1(String answerText1) {
		this.answerText1 = answerText1;
	}

	public String getAnswerText2() {
		return answerText2;
	}

	public void setAnswerText2(String answerText2) {
		this.answerText2 = answerText2;
	}

	public String getTeacherId1() {
		return teacherId1;
	}

	public void setTeacherId1(String teacherId1) {
		this.teacherId1 = teacherId1;
	}

	public String getTeacherId2() {
		return teacherId2;
	}

	public void setTeacherId2(String teacherId2) {
		this.teacherId2 = teacherId2;
	}

	public String getBinary1() {
		return binary1;
	}

	public void setBinary1(String binary1) {
		this.binary1 = binary1;
	}

	public String getBinary2() {
		return binary2;
	}

	public void setBinary2(String binary2) {
		this.binary2 = binary2;
	}

	public String getDetailed1() {
		return detailed1;
	}

	public void setDetailed1(String detailed1) {
		this.detailed1 = detailed1;
	}

	public String getDetailed2() {
		return detailed2;
	}

	public void setDetailed2(String detailed2) {
		this.detailed2 = detailed2;
	}

	public String getDiagnosisId1() {
		return diagnosisId1;
	}

	public void setDiagnosisId1(String diagnosisId1) {
		this.diagnosisId1 = diagnosisId1;
	}

	public String getDiagnosisId2() {
		return diagnosisId2;
	}

	public void setDiagnosisId2(String diagnosisId2) {
		this.diagnosisId2 = diagnosisId2;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getClosestTaId1() {
		return closestTaId1;
	}

	public void setClosestTaId1(String closestTaId1) {
		this.closestTaId1 = closestTaId1;
	}

	public String getClosestTaId2() {
		return closestTaId2;
	}

	public void setClosestTaId2(String closestTaId2) {
		this.closestTaId2 = closestTaId2;
	}

	public String getTargetAnswerId() {
		return targetAnswerId;
	}

	public void setTargetAnswerId(String targetAnswerId) {
		this.targetAnswerId = targetAnswerId;
	}

	String answerText1;
	String answerText2;
	String teacherId1;
	String teacherId2;
	String binary1;
	String binary2;
	String detailed1;
	String detailed2;
	String diagnosisId1;
	String diagnosisId2;
	String question; 
	String closestTaId1;
	String closestTaId2;
	String targetAnswerId;

	public CregItem(String studentId, String questionId, String text, int grade, String targetAnwer) {
		super(studentId, questionId, text, grade, targetAnwer);
	}


}

