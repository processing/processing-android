package processing.mode.android;

import org.antlr.v4.runtime.TokenStream;
import processing.mode.java.preproc.PdeParseTreeListener;
import processing.mode.java.preproc.PdePreprocessor;


/**
 * Demo object to show how to create PdePreprocessors with custom code generator.
 *
 * <p>
 * Demo factory to show how to create PdePreprocessors with custom code generator where, in
 * practice, one would likely have this code within another object.
 * </p>
 */
public class AndroidPreprocessorFactory {

  /**
   * Build a new preprocessor.
   *
   * @param sketchName The name of the sketch to be preprocessed.
   * @return Newly created preprocessor.
   */
  public static PdePreprocessor build(String sketchName) {
    return build(sketchName);
  }

  /**
   * Build a new preprocessor.
   *
   * @param sketchName The name of the sketch to be preprocessed.
   * @param packageName The package in which the sketch encosing class should be a member.
   * @return Newly created preprocessor.
   */
  public static PdePreprocessor build(String sketchName, String packageName) {
    return PdePreprocessor.builderFor(sketchName) // New builder object
        .setParseTreeListenerFactory((tokens, name, tabSize) -> new AndroidTreeListener(
            tokens,
            name,
            tabSize,
            packageName
        ))
        .build();
  }

  /**
   * Subclass of the rewriter code generator to support Android.
   *
   * <p>
   * The rewriter code generator has some non-trivial logic to help generate difficult parts of the
   * sketch java translation including its "header" and "footer". The Android build requires a
   * package which is injected at the start of teh header contents.
   * </p>
   */
  private static class AndroidTreeListener extends PdeParseTreeListener {

    private final String packageName;

    /**
     * Create a new Android rewriter code generator.
     * 
     * @param tokens The sketch code as tokens.
     * @param newSketchName The name of the sketch.
     * @param newTabSize The size of the tabs in spaces.
     * @param newPackageName The package in which the sketch code should be placed.
     */
    public AndroidTreeListener(TokenStream tokens, String newSketchName, int newTabSize,
                               String newPackageName) {
      
      super(tokens, newSketchName, newTabSize);
      packageName = newPackageName;
    }

    /**
     * Override the header contents generation.
     *
     * @param writer The writer through which code edits can be made.
     * @param params The parameters for the code generator from the preprocessor as determined by
     *    sketch code and settings.
     * @param resultBuilder Builder for reporting out results to the caller.
     */
    @Override
    public void writeHeaderContents(PrintWriterWithEditGen writer, RewriteParams params,
                                    RewriteResultBuilder resultBuilder) {

      // Add the package before any other header content. This puts it at the top of the file.
      if (packageName != null) {
        writer.addCodeLine("package " + packageName + ";");
        writer.addEmptyLine();
      }

      // Write the remaining stuff.
      super.writeHeaderContents(writer, params, resultBuilder);
    }

    /**
     * Write the contents of the footer using a prebuilt print writer.
     *
     * @param decoratedWriter he writer though which the comment should be introduced.
     * @param params The parameters for the rewrite.
     * @param resultBuilder Builder for reporting out results to the caller.
     */
    protected void writeFooterContents(PrintWriterWithEditGen decoratedWriter, RewriteParams params,
                                       RewriteResultBuilder resultBuilder) {
      super.writeFooterContents(decoratedWriter, params, resultBuilder);
    }    

  }

}
