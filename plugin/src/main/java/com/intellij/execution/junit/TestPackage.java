/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.TestClassCollector;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.execution.impl.testframework.SearchForTestsTask;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PackageScope;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.rt.execution.junit.JUnitStarter;
import consulo.application.ReadAction;
import consulo.execution.CantRunException;
import consulo.execution.ExecutionBundle;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import consulo.junit.impl.JUnitProperties;
import org.jetbrains.annotations.TestOnly;

import jakarta.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class TestPackage extends TestObject
{

	public TestPackage(JUnitConfiguration configuration, ExecutionEnvironment environment)
	{
		super(configuration, environment);
	}

	@Nullable
	@Override
	public SourceScope getSourceScope()
	{
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		return data.getScope().getSourceScope(getConfiguration());
	}

	@Override
	public SearchForTestsTask createSearchingForTestsTask()
	{
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();

		return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket)
		{
			private final Set<PsiClass> myClasses = new HashSet<>();

			@Override
			protected void search()
			{
				myClasses.clear();
				final SourceScope sourceScope = getSourceScope();
				final Module module = getConfiguration().getConfigurationModule().getModule();
				if(sourceScope != null && !JUnitStarter.JUNIT5_PARAMETER.equals(getRunner()))
				{
					DumbService instance = DumbService.getInstance((Project) myProject);
					try
					{
						instance.setAlternativeResolveEnabled(true);
						final TestClassFilter classFilter = getClassFilter(data);
						LOG.assertTrue(classFilter.getBase() != null);
						long start = System.currentTimeMillis();
						if(JUnitProperties.JUNIT4_SEARCH_4_TESTS_IN_CLASSPATH)
						{
							String packageName = getPackageName(data);
							String[] classNames = TestClassCollector.collectClassFQNames(packageName, getRootPath(), getConfiguration(), TestPackage::createPredicate);
							PsiManager manager = PsiManager.getInstance((Project) myProject);
							Arrays.stream(classNames).filter(className -> acceptClassName(className)) //check patterns
									.map(name -> ReadAction.<PsiClass, RuntimeException>compute(() -> ClassUtil.findPsiClass(manager, name, null, true, classFilter.getScope()))).filter(Objects
									::nonNull).forEach(myClasses::add);
							LOG.info("Found tests in " + (System.currentTimeMillis() - start));
						}
						else
						{
							ConfigurationUtil.findAllTestClasses(classFilter, module, myClasses);
						}
					}
					catch(CantRunException ignored)
					{
					}
					finally
					{
						instance.setAlternativeResolveEnabled(false);
					}
				}
			}

			@Override
			protected void onFound()
			{

				try
				{
					addClassesListToJavaParameters(myClasses, psiClass -> psiClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(psiClass) : null, getPackageName(data), createTempFiles(),
							getJavaParameters());
				}
				catch(ExecutionException ignored)
				{
				}
			}
		};
	}

	@Nullable
	protected Path getRootPath()
	{
		Module module = getConfiguration().getConfigurationModule().getModule();
		boolean chooseSingleModule = getConfiguration().getTestSearchScope() == TestSearchScope.SINGLE_MODULE;
		return TestClassCollector.getRootPath(module, chooseSingleModule);
	}

	protected boolean acceptClassName(String className)
	{
		return true;
	}

	protected boolean createTempFiles()
	{
		return false;
	}

	protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException
	{
		return getPackage(data).getQualifiedName();
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = super.createJavaParameters();

		createTempFiles(javaParameters);

		createServerSocket(javaParameters);
		return javaParameters;
	}

	@Override
	protected boolean configureByModule(Module module)
	{
		return super.configureByModule(module) && getConfiguration().getPersistentData().getScope() != TestSearchScope.WHOLE_PROJECT;
	}

	protected TestClassFilter getClassFilter(final JUnitConfiguration.Data data) throws CantRunException
	{
		Module module = getConfiguration().getConfigurationModule().getModule();
		if(getConfiguration().getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT)
		{
			module = null;
		}
		final TestClassFilter classFilter = TestClassFilter.create(getSourceScope(), module);
		return classFilter.intersectionWith(filterScope(data));
	}

	protected GlobalSearchScope filterScope(final JUnitConfiguration.Data data) throws CantRunException
	{
		final Ref<CantRunException> ref = new Ref<>();
		final GlobalSearchScope aPackage = ReadAction.compute(() ->
		{
			try
			{
				return PackageScope.packageScope((PsiJavaPackage) getPackage(data), true);
			}
			catch(CantRunException e)
			{
				ref.set(e);
				return null;
			}
		});
		final CantRunException exception = ref.get();
		if(exception != null)
		{
			throw exception;
		}
		return aPackage;
	}

	protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException
	{
		final Project project = getConfiguration().getProject();
		final String packageName = data.getPackageName();
		final PsiManager psiManager = PsiManager.getInstance(project);
		final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName);
		if(aPackage == null)
		{
			throw CantRunException.packageNotFound(packageName);
		}
		return aPackage;
	}

	@Override
	public String suggestActionName()
	{
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		if(data.getPackageName().trim().length() > 0)
		{
			return ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName());
		}
		return ExecutionBundle.message("all.tests.scope.presentable.text");
	}

	@Override
	public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration)
	{
		if(!(element instanceof PsiPackage))
		{
			return null;
		}
		return RefactoringListeners.getListener((PsiJavaPackage) element, configuration.myPackage);
	}

	@Override
	public boolean isConfiguredByElement(final JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir)
	{
		return testPackage != null && Comparing.equal(testPackage.getQualifiedName(), configuration.getPersistentData().getPackageName());
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();
		final String packageName = getConfiguration().getPersistentData().getPackageName();
		final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
		if(aPackage == null)
		{
			throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", packageName));
		}
		if(getSourceScope() == null)
		{
			getConfiguration().getConfigurationModule().checkForWarning();
		}
	}

	@TestOnly
	public File getWorkingDirsFile()
	{
		return myWorkingDirsFile;
	}

	private static Predicate<Class<?>> createPredicate(ClassLoader classLoader)
	{

		Class<?> testCaseClass = loadClass(classLoader, "junit.framework.TestCase");

		@SuppressWarnings("unchecked") Class<? extends Annotation> runWithAnnotationClass = (Class<? extends Annotation>) loadClass(classLoader, "org.junit.runner.RunWith");

		@SuppressWarnings("unchecked") Class<? extends Annotation> testAnnotationClass = (Class<? extends Annotation>) loadClass(classLoader, "org.junit.Test");

		return aClass ->
		{
			//annotation
			if(runWithAnnotationClass != null && aClass.isAnnotationPresent(runWithAnnotationClass))
			{
				return true;
			}
			//junit 3
			if(testCaseClass != null && testCaseClass.isAssignableFrom(aClass))
			{
				return Arrays.stream(aClass.getConstructors()).anyMatch(constructor ->
				{
					Class<?>[] parameterTypes = constructor.getParameterTypes();
					return parameterTypes.length == 0 || parameterTypes.length == 1 && CommonClassNames.JAVA_LANG_STRING.equals(parameterTypes[0].getName());
				});
			}
			else
			{
				//junit 4 & suite
				for(Method method : aClass.getMethods())
				{
					if(Modifier.isStatic(method.getModifiers()) && "suite".equals(method.getName()))
					{
						return true;
					}
					if(testAnnotationClass != null && method.isAnnotationPresent(testAnnotationClass))
					{
						return hasSingleConstructor(aClass);
					}
				}
			}
			return false;
		};
	}

	private static Class<?> loadClass(ClassLoader classLoader, String className)
	{
		try
		{
			return Class.forName(className, true, classLoader);
		}
		catch(ClassNotFoundException e)
		{
			return null;
		}
	}

	private static boolean hasSingleConstructor(Class<?> aClass)
	{
		Constructor<?>[] constructors = aClass.getConstructors();
		return constructors.length == 1 && constructors[0].getParameterTypes().length == 0;
	}
}
