

/* First created by JCasGen Mon Apr 10 10:56:41 MDT 2017 */
package edu.utah.bmi.type.system;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;



/** an automatic generated concept type
 * Updated by JCasGen Mon Apr 10 10:56:41 MDT 2017
 * XML source: desc/type/customized.xml
 * @generated */
public class Dx_TEST extends Concept {
  /** @generated
   * @ordered 
   */
  @SuppressWarnings ("hiding")
  public final static int typeIndexID = JCasRegistry.register(Dx_TEST.class);
  /** @generated
   * @ordered 
   */
  @SuppressWarnings ("hiding")
  public final static int type = typeIndexID;
  /** @generated
   * @return index of the type  
   */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Dx_TEST() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated
   * @param addr low level Feature Structure reference
   * @param type the type of this Feature Structure 
   */
  public Dx_TEST(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated
   * @param jcas JCas to which this Feature Structure belongs 
   */
  public Dx_TEST(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated
   * @param jcas JCas to which this Feature Structure belongs
   * @param begin offset to the begin spot in the SofA
   * @param end offset to the end spot in the SofA 
  */  
  public Dx_TEST(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** 
   * <!-- begin-user-doc -->
   * Write your own initialization here
   * <!-- end-user-doc -->
   *
   * @generated modifiable 
   */
  private void readObject() {/*default - does nothing empty block */}
     
 
    
  //*--------------*
  //* Feature: Toxic

  /** getter for Toxic - gets Automatic generated Type
   * @generated
   * @return value of the feature 
   */
  public String getToxic() {
    if (Dx_TEST_Type.featOkTst && ((Dx_TEST_Type)jcasType).casFeat_Toxic == null)
      jcasType.jcas.throwFeatMissing("Toxic", "edu.utah.bmi.type.system.Dx_TEST");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Dx_TEST_Type)jcasType).casFeatCode_Toxic);}
    
  /** setter for Toxic - sets Automatic generated Type 
   * @generated
   * @param v value to set into the feature 
   */
  public void setToxic(String v) {
    if (Dx_TEST_Type.featOkTst && ((Dx_TEST_Type)jcasType).casFeat_Toxic == null)
      jcasType.jcas.throwFeatMissing("Toxic", "edu.utah.bmi.type.system.Dx_TEST");
    jcasType.ll_cas.ll_setStringValue(addr, ((Dx_TEST_Type)jcasType).casFeatCode_Toxic, v);}    
   
    
  //*--------------*
  //* Feature: TestType

  /** getter for TestType - gets Automatic generated Type
   * @generated
   * @return value of the feature 
   */
  public String getTestType() {
    if (Dx_TEST_Type.featOkTst && ((Dx_TEST_Type)jcasType).casFeat_TestType == null)
      jcasType.jcas.throwFeatMissing("TestType", "edu.utah.bmi.type.system.Dx_TEST");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Dx_TEST_Type)jcasType).casFeatCode_TestType);}
    
  /** setter for TestType - sets Automatic generated Type 
   * @generated
   * @param v value to set into the feature 
   */
  public void setTestType(String v) {
    if (Dx_TEST_Type.featOkTst && ((Dx_TEST_Type)jcasType).casFeat_TestType == null)
      jcasType.jcas.throwFeatMissing("TestType", "edu.utah.bmi.type.system.Dx_TEST");
    jcasType.ll_cas.ll_setStringValue(addr, ((Dx_TEST_Type)jcasType).casFeatCode_TestType, v);}    
  }

    