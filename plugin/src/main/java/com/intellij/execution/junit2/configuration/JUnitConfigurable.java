/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.impl.MethodBrowser;
import com.intellij.java.execution.impl.testDiscovery.TestDiscoveryExtension;
import com.intellij.java.execution.impl.ui.*;
import com.intellij.java.language.impl.ui.EditorTextFieldWithBrowseButton;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import consulo.execution.ExecutionBundle;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.execution.ui.awt.BrowseModuleValueActionListener;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.ui.awt.ModuleDescriptionsComboBox;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class JUnitConfigurable<T extends JUnitConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
    private static final List<IntList> ourEnabledFields = Arrays.asList(
        IntLists.newArrayList(new int[]{0}),
        IntLists.newArrayList(new int[]{1}),
        IntLists.newArrayList(new int[]{
            1,
            2
        }),
        IntLists.newArrayList(new int[]{3}),
        IntLists.newArrayList(new int[]{4}),
        IntLists.newArrayList(new int[]{5}),
        IntLists.newArrayList(new int[]{
            1,
            2
        }),
        IntLists.newArrayList(new int[]{6})
    );
    private static final String[] FORK_MODE_ALL = {
        JUnitConfiguration.FORK_NONE,
        JUnitConfiguration.FORK_METHOD,
        JUnitConfiguration.FORK_KLASS
    };
    private static final String[] FORK_MODE = {
        JUnitConfiguration.FORK_NONE,
        JUnitConfiguration.FORK_METHOD
    };
    private final ConfigurationModuleSelector myModuleSelector;
    private final LabeledComponent[] myTestLocations = new LabeledComponent[6];
    private final JUnitConfigurationModel myModel;
    private final BrowseModuleValueActionListener[] myBrowsers;
    private JComponent myPackagePanel;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
    private LabeledComponent<TextFieldWithBrowseButton> myDir;
    private LabeledComponent<JPanel> myPattern;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myMethod;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myCategory;
    // Fields
    private JPanel myWholePanel;
    private LabeledComponent<ModuleDescriptionsComboBox> myModule;
    private CommonJavaParametersPanel myCommonJavaParameters;
    private JRadioButton myWholeProjectScope;
    private JRadioButton mySingleModuleScope;
    private JRadioButton myModuleWDScope;
    private TextFieldWithBrowseButton myPatternTextField;
    private JrePathEditor myJrePathEditor;
    private LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
    private JComboBox myForkCb;
    private JBLabel myTestLabel;
    private JComboBox myTypeChooser;
    private JBLabel mySearchForTestsLabel;
    private JPanel myScopesPanel;
    private JComboBox myRepeatCb;
    private JTextField myRepeatCountField;
    private LabeledComponent<JComboBox<String>> myChangeListLabeledComponent;
    private LabeledComponent<RawCommandLineEditor> myUniqueIdField;
    private Project myProject;
    private JComponent anchor;

    public JUnitConfigurable(final Project project) {
        myProject = project;
        myModel = new JUnitConfigurationModel(project);
        $$$setupUI$$$();
        myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
        myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(getModulesComponent(), false));
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
        myCommonJavaParameters.setHasModuleMacro();
        myModule.getComponent().addActionListener(e -> myCommonJavaParameters.setModuleContext(myModuleSelector.getModule()));
        myBrowsers = new BrowseModuleValueActionListener[]{
            new PackageChooserActionListener(project),
            new TestClassBrowser(project),
            new MethodBrowser(project) {
                @Override
                protected Condition<PsiMethod> getFilter(PsiClass testClass) {
                    return new JUnitUtil.TestMethodFilter(testClass);
                }

                @Override
                protected String getClassName() {
                    return JUnitConfigurable.this.getClassName();
                }

                @Override
                protected ConfigurationModuleSelector getModuleSelector() {
                    return myModuleSelector;
                }
            },
            new TestsChooserActionListener(project),
            new BrowseModuleValueActionListener(project) {
                @Override
                protected String showDialog() {
                    final VirtualFile virtualFile =
                        IdeaFileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
                    if (virtualFile != null) {
                        return FileUtil.toSystemDependentName(virtualFile.getPath());
                    }
                    return null;
                }
            },
            new CategoryBrowser(project),
            null
        };
        // Garbage support
        final DefaultComboBoxModel aModel = new DefaultComboBoxModel();
        aModel.addElement(JUnitConfigurationModel.ALL_IN_PACKAGE);
        aModel.addElement(JUnitConfigurationModel.DIR);
        aModel.addElement(JUnitConfigurationModel.PATTERN);
        aModel.addElement(JUnitConfigurationModel.CLASS);
        aModel.addElement(JUnitConfigurationModel.METHOD);
        aModel.addElement(JUnitConfigurationModel.CATEGORY);
        aModel.addElement(JUnitConfigurationModel.UNIQUE_ID);
        if (TestDiscoveryExtension.TESTDISCOVERY_ENABLED) {
            aModel.addElement(JUnitConfigurationModel.BY_SOURCE_POSITION);
            aModel.addElement(JUnitConfigurationModel.BY_SOURCE_CHANGES);
        }
        myTypeChooser.setModel(aModel);
        myTypeChooser.setRenderer(new ListCellRendererWrapper<Integer>() {
            @Override
            public void customize(JList list, Integer value, int index, boolean selected, boolean hasFocus) {
                switch (value) {
                    case JUnitConfigurationModel.ALL_IN_PACKAGE:
                        setText("All in package");
                        break;
                    case JUnitConfigurationModel.DIR:
                        setText("All in directory");
                        break;
                    case JUnitConfigurationModel.PATTERN:
                        setText("Pattern");
                        break;
                    case JUnitConfigurationModel.CLASS:
                        setText("Class");
                        break;
                    case JUnitConfigurationModel.METHOD:
                        setText("Method");
                        break;
                    case JUnitConfigurationModel.CATEGORY:
                        setText("Category");
                        break;
                    case JUnitConfigurationModel.UNIQUE_ID:
                        setText("UniqueId");
                        break;
                    case JUnitConfigurationModel.BY_SOURCE_POSITION:
                        setText("Through source location");
                        break;
                    case JUnitConfigurationModel.BY_SOURCE_CHANGES:
                        setText("Over changes in sources");
                        break;
                }
            }
        });

        myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
        myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
        myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
        myTestLocations[JUnitConfigurationModel.DIR] = myDir;
        myTestLocations[JUnitConfigurationModel.CATEGORY] = myCategory;

        myRepeatCb.setModel(new DefaultComboBoxModel(RepeatCount.REPEAT_TYPES));
        myRepeatCb.setSelectedItem(RepeatCount.ONCE);
        myRepeatCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                myRepeatCountField.setEnabled(RepeatCount.N.equals(myRepeatCb.getSelectedItem()));
            }
        });

        final JPanel panel = myPattern.getComponent();
        panel.setLayout(new BorderLayout());
        myPatternTextField = new TextFieldWithBrowseButton();
        myPatternTextField.setButtonIcon(PlatformIconGroup.generalAdd());
        panel.add(myPatternTextField, BorderLayout.CENTER);
        myTestLocations[JUnitConfigurationModel.PATTERN] = myPattern;

        final FileChooserDescriptor dirFileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        dirFileChooser.setHideIgnored(false);
        final JTextField textField = myDir.getComponent().getTextField();
        InsertPathAction.addTo(textField, dirFileChooser);
        FileChooserFactory.getInstance().installFileCompletion(textField, dirFileChooser, true, null);
        // Done

        myModel.setListener(this);

        myTypeChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Object selectedItem = myTypeChooser.getSelectedItem();
                myModel.setType((Integer)selectedItem);
                changePanel();
            }
        });

        myRepeatCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ((Integer)myTypeChooser.getSelectedItem() == JUnitConfigurationModel.CLASS) {
                    myForkCb.setModel(getForkModelBasedOnRepeat());
                }
            }
        });
        myModel.setType(JUnitConfigurationModel.CLASS);
        installDocuments();
        addRadioButtonsListeners(new JRadioButton[]{
            myWholeProjectScope,
            mySingleModuleScope,
            myModuleWDScope
        }, null);
        myWholeProjectScope.addChangeListener(e -> onScopeChanged());

        UIUtil.setEnabled(myCommonJavaParameters.getProgramParametersComponent(), false, true);

        setAnchor(mySearchForTestsLabel);
        myJrePathEditor.setAnchor(myModule.getLabel());
        myCommonJavaParameters.setAnchor(myModule.getLabel());
        myShortenClasspathModeCombo.setAnchor(myModule.getLabel());

        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        myChangeListLabeledComponent.getComponent().setModel(model);
        model.addElement("All");

        final List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
        for (LocalChangeList changeList : changeLists) {
            model.addElement(changeList.getName());
        }

        myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent()));
    }

    private static void addRadioButtonsListeners(final JRadioButton[] radioButtons, ChangeListener listener) {
        final ButtonGroup group = new ButtonGroup();
        for (final JRadioButton radioButton : radioButtons) {
            radioButton.getModel().addChangeListener(listener);
            group.add(radioButton);
        }
        if (group.getSelection() == null) {
            group.setSelected(radioButtons[0].getModel(), true);
        }
    }

    @Override
    public void applyEditorTo(@Nonnull final JUnitConfiguration configuration) {
        configuration.setRepeatMode((String)myRepeatCb.getSelectedItem());
        try {
            configuration.setRepeatCount(Integer.parseInt(myRepeatCountField.getText()));
        }
        catch (NumberFormatException e) {
            configuration.setRepeatCount(1);
        }
        myModel.apply(getModuleSelector().getModule(), configuration);
        configuration.getPersistentData().setUniqueIds(myUniqueIdField.getComponent().getText().split(" "));
        configuration.getPersistentData().setChangeList((String)myChangeListLabeledComponent.getComponent().getSelectedItem());
        applyHelpersTo(configuration);
        final JUnitConfiguration.Data data = configuration.getPersistentData();
        if (myWholeProjectScope.isSelected()) {
            data.setScope(TestSearchScope.WHOLE_PROJECT);
        }
        else if (mySingleModuleScope.isSelected()) {
            data.setScope(TestSearchScope.SINGLE_MODULE);
        }
        else if (myModuleWDScope.isSelected()) {
            data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
        }
        configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
        configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());

        myCommonJavaParameters.applyTo(configuration);
        configuration.setForkMode((String)myForkCb.getSelectedItem());
        configuration.setShortenCommandLine((ShortenCommandLine)myShortenClasspathModeCombo.getComponent().getSelectedItem());
    }

    @Override
    public void resetEditorFrom(@Nonnull final JUnitConfiguration configuration) {
        final int count = configuration.getRepeatCount();
        myRepeatCountField.setText(String.valueOf(count));
        myRepeatCountField.setEnabled(count > 1);
        myRepeatCb.setSelectedItem(configuration.getRepeatMode());

        myModel.reset(configuration);
        myChangeListLabeledComponent.getComponent().setSelectedItem(configuration.getPersistentData().getChangeList());
        String[] ids = configuration.getPersistentData().getUniqueIds();
        myUniqueIdField.getComponent().setText(ids != null ? StringUtil.join(ids, " ") : null);
        myCommonJavaParameters.reset(configuration);
        getModuleSelector().reset(configuration);
        final TestSearchScope scope = configuration.getPersistentData().getScope();
        if (scope == TestSearchScope.SINGLE_MODULE) {
            mySingleModuleScope.setSelected(true);
        }
        else if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES) {
            myModuleWDScope.setSelected(true);
        }
        else {
            myWholeProjectScope.setSelected(true);
        }
        myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
        myForkCb.setSelectedItem(configuration.getForkMode());
        myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    }

    private void changePanel() {
        String selectedItem = (String)myForkCb.getSelectedItem();
        if (selectedItem == null) {
            selectedItem = JUnitConfiguration.FORK_NONE;
        }
        final Integer selectedType = (Integer)myTypeChooser.getSelectedItem();
        if (selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE) {
            myPackagePanel.setVisible(true);
            myScopesPanel.setVisible(true);
            myPattern.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myMethod.setVisible(false);
            myDir.setVisible(false);
            myChangeListLabeledComponent.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
        else if (selectedType == JUnitConfigurationModel.DIR) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(false);
            myDir.setVisible(true);
            myPattern.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myChangeListLabeledComponent.setVisible(false);
            myMethod.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
        else if (selectedType == JUnitConfigurationModel.CLASS) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(false);
            myPattern.setVisible(false);
            myDir.setVisible(false);
            myClass.setVisible(true);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myChangeListLabeledComponent.setVisible(false);
            myMethod.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(getForkModelBasedOnRepeat());
            myForkCb.setSelectedItem(selectedItem != JUnitConfiguration.FORK_KLASS ? selectedItem : JUnitConfiguration.FORK_METHOD);
        }
        else if (selectedType == JUnitConfigurationModel.METHOD || selectedType == JUnitConfigurationModel.BY_SOURCE_POSITION) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(false);
            myPattern.setVisible(false);
            myDir.setVisible(false);
            myClass.setVisible(true);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myMethod.setVisible(true);
            myChangeListLabeledComponent.setVisible(false);
            myForkCb.setEnabled(false);
            myForkCb.setSelectedItem(JUnitConfiguration.FORK_NONE);
        }
        else if (selectedType == JUnitConfigurationModel.CATEGORY) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(true);
            myDir.setVisible(false);
            myPattern.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(true);
            myUniqueIdField.setVisible(false);
            myMethod.setVisible(false);
            myChangeListLabeledComponent.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
        else if (selectedType == JUnitConfigurationModel.BY_SOURCE_CHANGES) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(false);
            myDir.setVisible(false);
            myPattern.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myMethod.setVisible(false);
            myChangeListLabeledComponent.setVisible(true);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
        else if (selectedType == JUnitConfigurationModel.UNIQUE_ID) {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(false);
            myDir.setVisible(false);
            myPattern.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(true);
            myMethod.setVisible(false);
            myChangeListLabeledComponent.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
        else {
            myPackagePanel.setVisible(false);
            myScopesPanel.setVisible(true);
            myPattern.setVisible(true);
            myDir.setVisible(false);
            myClass.setVisible(false);
            myCategory.setVisible(false);
            myUniqueIdField.setVisible(false);
            myMethod.setVisible(true);
            myChangeListLabeledComponent.setVisible(false);
            myForkCb.setEnabled(true);
            myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
            myForkCb.setSelectedItem(selectedItem);
        }
    }

    private DefaultComboBoxModel getForkModelBasedOnRepeat() {
        return new DefaultComboBoxModel(RepeatCount.ONCE.equals(myRepeatCb.getSelectedItem()) ? FORK_MODE : FORK_MODE_ALL);
    }

    public ModuleDescriptionsComboBox getModulesComponent() {
        return myModule.getComponent();
    }

    public ConfigurationModuleSelector getModuleSelector() {
        return myModuleSelector;
    }

    private void installDocuments() {
        for (int i = 0; i < myTestLocations.length; i++) {
            final LabeledComponent testLocation = getTestLocation(i);
            final JComponent component = testLocation.getComponent();
            final ComponentWithBrowseButton field;
            Object document;
            if (component instanceof TextFieldWithBrowseButton textFieldWithBrowseButton) {
                field = textFieldWithBrowseButton;
                document = new PlainDocument();
                textFieldWithBrowseButton.getTextField().setDocument((Document)document);
            }
            else if (component instanceof EditorTextFieldWithBrowseButton editorTextFieldWithBrowseButton) {
                field = editorTextFieldWithBrowseButton;
                document = ((EditorTextField)field.getChildComponent()).getDocument();
            }
            else {
                field = myPatternTextField;
                document = new PlainDocument();
                ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
            }
            myBrowsers[i].setField(field);
            if (myBrowsers[i] instanceof MethodBrowser methodBrowser) {
                final EditorTextField childComponent = (EditorTextField)field.getChildComponent();
                methodBrowser.installCompletion(childComponent);
                document = childComponent.getDocument();
            }
            myModel.setJUnitDocument(i, document);
        }
    }

    public LabeledComponent getTestLocation(final int index) {
        return myTestLocations[index];
    }

    private void createUIComponents() {
        myPackage = new LabeledComponent<>();
        myPackage.setComponent(new EditorTextFieldWithBrowseButton(myProject, false));

        myClass = new LabeledComponent<>();
        final TestClassBrowser classBrowser = new TestClassBrowser(myProject);
        myClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
            @Override
            public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
                try {
                    if (declaration instanceof PsiClass psiClass && (
                        classBrowser.getFilter().isAccepted(psiClass)
                            || classBrowser.findClass(psiClass.getQualifiedName()) != null && place.getParent() != null
                    )) {
                        return Visibility.VISIBLE;
                    }
                }
                catch (ClassBrowser.NoFilterException e) {
                    return Visibility.NOT_VISIBLE;
                }
                return Visibility.NOT_VISIBLE;
            }
        }));

        myCategory = new LabeledComponent<>();
        myCategory.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
            @Override
            public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
                if (declaration instanceof PsiClass) {
                    return Visibility.VISIBLE;
                }
                return Visibility.NOT_VISIBLE;
            }
        }));

        myMethod = new LabeledComponent<>();
        final EditorTextFieldWithBrowseButton textFieldWithBrowseButton = new EditorTextFieldWithBrowseButton(
            myProject,
            true,
            JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
            PlainTextLanguage.INSTANCE.getAssociatedFileType()
        );
        myMethod.setComponent(textFieldWithBrowseButton);

        myShortenClasspathModeCombo = new LabeledComponent<>();
    }

    @Override
    public JComponent getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(JComponent anchor) {
        this.anchor = anchor;
        mySearchForTestsLabel.setAnchor(anchor);
        myTestLabel.setAnchor(anchor);
        myClass.setAnchor(anchor);
        myDir.setAnchor(anchor);
        myMethod.setAnchor(anchor);
        myPattern.setAnchor(anchor);
        myPackage.setAnchor(anchor);
        myCategory.setAnchor(anchor);
        myUniqueIdField.setAnchor(anchor);
        myChangeListLabeledComponent.setAnchor(anchor);
    }

    public void onTypeChanged(final int newType) {
        myTypeChooser.setSelectedItem(newType);
        final IntList enabledFields = ourEnabledFields.get(newType);
        for (int i = 0; i < myTestLocations.length; i++) {
            getTestLocation(i).setEnabled(enabledFields.contains(i));
        }
    /*if (newType == JUnitConfigurationModel.PATTERN) {
      myModule.setEnabled(false);
    } else */
        if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE && newType != JUnitConfigurationModel.PATTERN && newType != JUnitConfigurationModel.CATEGORY && newType != JUnitConfigurationModel
            .UNIQUE_ID) {
            myModule.setEnabled(true);
        }
        else {
            onScopeChanged();
        }
    }

    private void onScopeChanged() {
        final Integer selectedItem = (Integer)myTypeChooser.getSelectedItem();
        final boolean allInPackageAllInProject =
            (selectedItem == JUnitConfigurationModel.ALL_IN_PACKAGE
                || selectedItem == JUnitConfigurationModel.PATTERN
                || selectedItem == JUnitConfigurationModel.CATEGORY
                || selectedItem == JUnitConfigurationModel.UNIQUE_ID)
                && myWholeProjectScope.isSelected();
        myModule.setEnabled(!allInPackageAllInProject);
        if (allInPackageAllInProject) {
            myModule.getComponent().setSelectedItem(null);
        }
    }

    private String getClassName() {
        return ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CLASS)).getComponent().getText();
    }

    private void setPackage(final PsiPackage aPackage) {
        if (aPackage == null) {
            return;
        }
        ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.ALL_IN_PACKAGE)).getComponent()
            .setText(aPackage.getQualifiedName());
    }

    @Nonnull
    @Override
    public JComponent createEditor() {
        return myWholePanel;
    }

    private void applyHelpersTo(final JUnitConfiguration currentState) {
        myCommonJavaParameters.applyTo(currentState);
        getModuleSelector().applyTo(currentState);
    }

    private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
        public PackageChooserActionListener(final Project project) {
            super(project);
        }

        @Override
        protected String showDialog() {
            PackageChooser chooser = getProject().getInstance(PackageChooserFactory.class).create();
            List<PsiJavaPackage> packages = chooser.showAndSelect();
            final PsiPackage aPackage = packages == null || packages.isEmpty() ? null : packages.getFirst();
            return aPackage != null ? aPackage.getQualifiedName() : null;
        }
    }

    private class TestsChooserActionListener extends TestClassBrowser {
        public TestsChooserActionListener(final Project project) {
            super(project);
        }

        @Override
        protected void onClassChoosen(PsiClass psiClass) {
            final JTextField textField = myPatternTextField.getTextField();
            final String text = textField.getText();
            textField.setText(text + (text.length() > 0 ? "||" : "") + psiClass.getQualifiedName());
        }

        @Override
        protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
            return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showDialog();
        }
    }

    private class TestClassBrowser extends ClassBrowser {
        public TestClassBrowser(final Project project) {
            super(project, ExecutionBundle.message("choose.test.class.dialog.title"));
        }

        @Override
        protected void onClassChoosen(final PsiClass psiClass) {
            setPackage(JUnitUtil.getContainingPackage(psiClass));
        }

        @Override
        protected PsiClass findClass(final String className) {
            return getModuleSelector().findClass(className);
        }

        @Override
        protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
            final ConfigurationModuleSelector moduleSelector = getModuleSelector();
            final Module module = moduleSelector.getModule();
            if (module == null) {
                throw NoFilterException.moduleDoesntExist(moduleSelector);
            }
            final ClassFilter.ClassFilterWithScope classFilter;
            final JUnitConfiguration configurationCopy =
                    new JUnitConfiguration(
                            ExecutionBundle.message("default.junit.configuration.name"),
                            getProject(),
                            JUnitConfigurationType.getInstance()
                                    .getConfigurationFactories()[0]
                    );
            applyEditorTo(configurationCopy);
            classFilter = TestClassFilter.create(
                    SourceScope.modulesWithDependencies(configurationCopy.getModules()),
                    configurationCopy.getConfigurationModule().getModule()
            );
            return classFilter;
        }
    }

    private class CategoryBrowser extends ClassBrowser {
        public CategoryBrowser(Project project) {
            super(project, "Category Interface");
        }

        @Override
        protected PsiClass findClass(final String className) {
            return myModuleSelector.findClass(className);
        }

        @Override
        protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
            final Module module = myModuleSelector.getModule();
            final GlobalSearchScope scope;
            if (module == null) {
                scope = GlobalSearchScope.allScope(myProject);
            }
            else {
                scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
            }
            return new ClassFilter.ClassFilterWithScope() {
                @Override
                public GlobalSearchScope getScope() {
                    return scope;
                }

                @Override
                public boolean isAccepted(final PsiClass aClass) {
                    return true;
                }
            };
        }

        @Override
        protected void onClassChoosen(PsiClass psiClass) {
            ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CATEGORY)).getComponent()
                .setText(psiClass.getQualifiedName());
        }
    }

    /**
     * Method generated by Consulo GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        myWholePanel.add(
            panel1,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_NORTH,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                    .SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myTestLabel = new JBLabel();
        myTestLabel.setHorizontalAlignment(2);
        myTestLabel.setHorizontalTextPosition(2);
        myTestLabel.setIconTextGap(4);
        this.$$$loadLabelText$$$(
            myTestLabel,
            ResourceBundle.getBundle("consulo/execution/ExecutionBundle")
                .getString("junit.configuration.configure.junit.test.kind.label")
        );
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(myTestLabel, gbc);
        myTypeChooser = new JComboBox();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, JBUI.scale(10), 0, 0);
        panel1.add(myTypeChooser, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(spacer1, gbc);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
        myWholePanel.add(
            panel2,
            new GridConstraints(
                3,
                0,
                1,
                2,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                    .SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        myCommonJavaParameters = new CommonJavaParametersPanel();
        panel2.add(
            myCommonJavaParameters,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                    .SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myModule = new LabeledComponent();
        myModule.setComponent(new ModuleDescriptionsComboBox());
        myModule.setEnabled(true);
        myModule.setLabelLocation("West");
        myModule.setText(ResourceBundle.getBundle("messages/JavaExecutionBundle")
            .getString("application.configuration.use.classpath.and.jdk.of.module.label"));
        panel2.add(
            myModule,
            new GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                    .SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myShortenClasspathModeCombo.setEnabled(true);
        myShortenClasspathModeCombo.setLabelLocation("West");
        myShortenClasspathModeCombo.setText(ResourceBundle.getBundle("messages/JavaExecutionBundle")
            .getString("application.configuration.shorten.command.line.label"));
        panel2.add(
            myShortenClasspathModeCombo,
            new GridConstraints(
                4,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                    GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer2 = new Spacer();
        panel2.add(
            spacer2,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                1,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                new Dimension(-1, JBUI.scale(10)),
                null,
                0,
                false
            )
        );
        final Spacer spacer3 = new Spacer();
        panel2.add(
            spacer3,
            new GridConstraints(
                5,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                1,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                    .SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        myJrePathEditor = new JrePathEditor();
        panel2.add(
            myJrePathEditor,
            new GridConstraints(
                3,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 6, new Insets(0, 0, 0, 0), -1, -1));
        myWholePanel.add(
            panel3,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_NORTH,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer4 = new Spacer();
        panel3.add(
            spacer4,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JLabel label1 = new JLabel();
        label1.setText("Repeat:");
        label1.setDisplayedMnemonic('R');
        label1.setDisplayedMnemonicIndex(0);
        panel3.add(label1, new GridConstraints(
            0,
            3,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false
        ));
        final JBLabel jBLabel1 = new JBLabel();
        panel3.add(
            jBLabel1,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myForkCb = new JComboBox();
        panel3.add(
            myForkCb,
            new GridConstraints(
                0,
                2,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myRepeatCountField = new JTextField();
        panel3.add(
            myRepeatCountField,
            new GridConstraints(
                0,
                5,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                new Dimension(JBUI.scale(30), -1),
                new Dimension(JBUI.scale(50), -1),
                new Dimension(
                    JBUI.scale(60),
                    -1
                ),
                0,
                false
            )
        );
        myRepeatCb = new JComboBox();
        panel3.add(
            myRepeatCb,
            new GridConstraints(
                0,
                4,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JSeparator separator1 = new JSeparator();
        myWholePanel.add(
            separator1,
            new GridConstraints(
                2,
                0,
                1,
                2,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints
                    .SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(9, 1, new Insets(0, 0, 0, 0), -1, -1));
        myWholePanel.add(
            panel4,
            new GridConstraints(
                1,
                0,
                1,
                2,
                GridConstraints.ANCHOR_NORTH,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        myMethod.setEnabled(true);
        myMethod.setLabelLocation("West");
        myMethod.setText(ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.method.label"));
        panel4.add(
            myMethod,
            new GridConstraints(
                7,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myPackagePanel = new JPanel();
        myPackagePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(
            myPackagePanel,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        myPackage.setEnabled(true);
        myPackage.setLabelLocation("West");
        myPackage.setText(ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.package.label"));
        myPackage.setVisible(true);
        myPackagePanel.add(
            myPackage,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myPattern = new LabeledComponent();
        myPattern.setComponent(new JPanel());
        myPattern.setLabelLocation("West");
        myPattern.setText("Pattern");
        myPattern.setVisible(true);
        panel4.add(
            myPattern,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myClass.setLabelLocation("West");
        myClass.setText(ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.class.label"));
        panel4.add(
            myClass,
            new GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myCategory.setLabelLocation("West");
        myCategory.setText("Category");
        panel4.add(
            myCategory,
            new GridConstraints(
                3,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myUniqueIdField = new LabeledComponent();
        myUniqueIdField.setComponent(new RawCommandLineEditor());
        myUniqueIdField.setLabelLocation("West");
        myUniqueIdField.setText("UniqueId");
        panel4.add(
            myUniqueIdField,
            new GridConstraints(
                4,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myDir = new LabeledComponent();
        myDir.setComponent(new TextFieldWithBrowseButton());
        myDir.setLabelLocation("West");
        myDir.setText("Directory");
        panel4.add(
            myDir,
            new GridConstraints(
                5,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myChangeListLabeledComponent = new LabeledComponent();
        myChangeListLabeledComponent.setComponent(new JComboBox<>());
        myChangeListLabeledComponent.setLabelLocation("West");
        myChangeListLabeledComponent.setText("Change list");
        panel4.add(
            myChangeListLabeledComponent,
            new GridConstraints(
                6,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myScopesPanel = new JPanel();
        myScopesPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(
            myScopesPanel,
            new GridConstraints(
                8,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        mySearchForTestsLabel = new JBLabel();
        this.$$$loadLabelText$$$(
            mySearchForTestsLabel,
            ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.search.for.tests.label")
        );
        myScopesPanel.add(
            mySearchForTestsLabel,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myWholeProjectScope = new JRadioButton();
        this.$$$loadButtonText$$$(
            myWholeProjectScope,
            ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.in.whole.project.radio")
        );
        myScopesPanel.add(
            myWholeProjectScope,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer5 = new Spacer();
        myScopesPanel.add(spacer5, new GridConstraints(
            0,
            2,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            1,
            null,
            null,
            null,
            0,
            false
        ));
        mySingleModuleScope = new JRadioButton();
        this.$$$loadButtonText$$$(
            mySingleModuleScope,
            ResourceBundle.getBundle("consulo/execution/ExecutionBundle").getString("junit.configuration.in.single.module.radio")
        );
        myScopesPanel.add(
            mySingleModuleScope,
            new GridConstraints(
                1,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myModuleWDScope = new JRadioButton();
        this.$$$loadButtonText$$$(
            myModuleWDScope,
            ResourceBundle.getBundle("consulo/execution/ExecutionBundle")
                .getString("junit.configuration.across.module.dependencies.radio")
        );
        myScopesPanel.add(
            myModuleWDScope,
            new GridConstraints(
                2,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        myTestLabel.setLabelFor(myTypeChooser);
        label1.setLabelFor(myRepeatCb);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) {
                    break;
                }
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) {
                    break;
                }
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}
