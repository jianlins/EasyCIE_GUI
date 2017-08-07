
/* First created by JCasGen Mon Apr 10 10:56:41 MDT 2017 */
package edu.utah.bmi.type.system;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.cas.Feature;

/** an automatic generated concept type
 * Updated by JCasGen Mon Apr 10 10:56:41 MDT 2017
 * @generated */
public class Dx_TEST_Type extends Concept_Type {
  /** @generated 
   * @return the generator for this type
   */
  @Override
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (Dx_TEST_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = Dx_TEST_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new Dx_TEST(addr, Dx_TEST_Type.this);
  			   Dx_TEST_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new Dx_TEST(addr, Dx_TEST_Type.this);
  	  }
    };
  /** @generated */
  @SuppressWarnings ("hiding")
  public final static int typeIndexID = Dx_TEST.typeIndexID;
  /** @generated 
     @modifiable */
  @SuppressWarnings ("hiding")
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("edu.utah.bmi.type.system.Dx_TEST");
 
  /** @generated */
  final Feature casFeat_Toxic;
  /** @generated */
  final int     casFeatCode_Toxic;
  /** @generated
   * @param addr low level Feature Structure reference
   * @return the feature value 
   */ 
  public String getToxic(int addr) {
        if (featOkTst && casFeat_Toxic == null)
      jcas.throwFeatMissing("Toxic", "edu.utah.bmi.type.system.Dx_TEST");
    return ll_cas.ll_getStringValue(addr, casFeatCode_Toxic);
  }
  /** @generated
   * @param addr low level Feature Structure reference
   * @param v value to set 
   */    
  public void setToxic(int addr, String v) {
        if (featOkTst && casFeat_Toxic == null)
      jcas.throwFeatMissing("Toxic", "edu.utah.bmi.type.system.Dx_TEST");
    ll_cas.ll_setStringValue(addr, casFeatCode_Toxic, v);}
    
  
 
  /** @generated */
  final Feature casFeat_TestType;
  /** @generated */
  final int     casFeatCode_TestType;
  /** @generated
   * @param addr low level Feature Structure reference
   * @return the feature value 
   */ 
  public String getTestType(int addr) {
        if (featOkTst && casFeat_TestType == null)
      jcas.throwFeatMissing("TestType", "edu.utah.bmi.type.system.Dx_TEST");
    return ll_cas.ll_getStringValue(addr, casFeatCode_TestType);
  }
  /** @generated
   * @param addr low level Feature Structure reference
   * @param v value to set 
   */    
  public void setTestType(int addr, String v) {
        if (featOkTst && casFeat_TestType == null)
      jcas.throwFeatMissing("TestType", "edu.utah.bmi.type.system.Dx_TEST");
    ll_cas.ll_setStringValue(addr, casFeatCode_TestType, v);}
    
  



  /** initialize variables to correspond with Cas Type and Features
	 * @generated
	 * @param jcas JCas
	 * @param casType Type 
	 */
  public Dx_TEST_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

 
    casFeat_Toxic = jcas.getRequiredFeatureDE(casType, "Toxic", "uima.cas.String", featOkTst);
    casFeatCode_Toxic  = (null == casFeat_Toxic) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_Toxic).getCode();

 
    casFeat_TestType = jcas.getRequiredFeatureDE(casType, "TestType", "uima.cas.String", featOkTst);
    casFeatCode_TestType  = (null == casFeat_TestType) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_TestType).getCode();

  }
}



    