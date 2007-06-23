/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package javax.faces.component;

import java.util.*;
import java.util.logging.*;

import javax.el.*;

import javax.faces.*;
import javax.faces.application.*;
import javax.faces.context.*;
import javax.faces.convert.*;
import javax.faces.el.*;
import javax.faces.event.*;
import javax.faces.render.*;
import javax.faces.validator.*;

public class UIInput extends UIOutput
  implements EditableValueHolder
{
  private static final Logger log
    = Logger.getLogger(UIInput.class.getName());
  
  public static final String COMPONENT_FAMILY = "javax.faces.Input";
  public static final String COMPONENT_TYPE = "javax.faces.Input";
  
  public static final String CONVERSION_MESSAGE_ID
    = "javax.faces.component.UIInput.CONVERSION";
  public static final String REQUIRED_MESSAGE_ID
    = "javax.faces.component.UIInput.REQUIRED";
  public static final String UPDATE_MESSAGE_ID
    = "javax.faces.component.UIInput.UPDATE";

  private static final Validator []NULL_VALIDATORS = new Validator[0];
  
  private static final HashMap<String,PropEnum> _propMap
    = new HashMap<String,PropEnum>();

  private ValueExpression _valueExpr;
  
  private Boolean _required;
  private ValueExpression _requiredExpr;
  
  private Boolean _immediate;
  private ValueExpression _immediateExpr;

  private String _requiredMessage;
  private ValueExpression _requiredMessageExpr;

  private String _converterMessage;
  private ValueExpression _converterMessageExpr;

  private String _validatorMessage;
  private ValueExpression _validatorMessageExpr;

  //

  private boolean _isValid = true;
  private boolean _isLocalValueSet;

  private Object _submittedValue;

  private ArrayList<Validator> _validatorList;
  private Validator []_validators = NULL_VALIDATORS;

  public UIInput()
  {
    setRendererType("javax.faces.Text");
  }

  /**
   * Returns the component family, used to select the renderer.
   */
  public String getFamily()
  {
    return COMPONENT_FAMILY;
  }

  //
  // properties
  //

  public String getRequiredMessage()
  {
    if (_requiredMessage != null)
      return _requiredMessage;
    else if (_requiredMessageExpr != null)
      return Util.evalString(_requiredMessageExpr, getFacesContext());
    else
      return null;
  }

  public void setRequiredMessage(String value)
  {
    _requiredMessage = value;
  }

  public String getConverterMessage()
  {
    if (_converterMessage != null)
      return _converterMessage;
    else if (_converterMessageExpr != null)
      return Util.evalString(_converterMessageExpr, getFacesContext());
    else
      return null;
  }

  public void setConverterMessage(String value)
  {
    _converterMessage = value;
  }

  public String getValidatorMessage()
  {
    if (_validatorMessage != null)
      return _validatorMessage;
    else if (_validatorMessageExpr != null)
      return Util.evalString(_validatorMessageExpr, getFacesContext());
    else
      return null;
  }

  public void setValidatorMessage(String value)
  {
    _validatorMessage = value;
  }

  /**
   * Returns the value expression with the given name.
   */
  @Override
  public ValueExpression getValueExpression(String name)
  {
    PropEnum prop = _propMap.get(name);

    if (prop != null) {
      switch (_propMap.get(name)) {
      case VALUE:
	return _valueExpr;
      case IMMEDIATE:
	return _immediateExpr;
      case REQUIRED:
	return _requiredExpr;
      case REQUIRED_MESSAGE:
	return _requiredMessageExpr;
      case CONVERTER_MESSAGE:
	return _converterMessageExpr;
      case VALIDATOR_MESSAGE:
	return _validatorMessageExpr;
      }
    }

    return super.getValueExpression(name);
  }

  /**
   * Sets the value expression with the given name.
   */
  @Override
  public void setValueExpression(String name, ValueExpression expr)
  {
    PropEnum prop = _propMap.get(name);

    if (prop != null) {
      switch (_propMap.get(name)) {
      case VALUE:
	if (expr != null && ! expr.isLiteralText())
	  _valueExpr = expr;
	super.setValueExpression(name, expr);
	return;
	
      case IMMEDIATE:
	if (expr != null && expr.isLiteralText())
	  _immediate = (Boolean) expr.getValue(null);
	else
	  _immediateExpr = expr;
	return;
	
      case REQUIRED:
	if (expr != null && expr.isLiteralText())
	  _required = (Boolean) expr.getValue(null);
	else
	  _requiredExpr = expr;
	return;
	
      case REQUIRED_MESSAGE:
	if (expr != null && expr.isLiteralText())
	  _requiredMessage = (String) expr.getValue(null);
	else
	  _requiredMessageExpr = expr;
	return;
	
      case CONVERTER_MESSAGE:
	if (expr != null && expr.isLiteralText())
	  _converterMessage = (String) expr.getValue(null);
	else
	  _converterMessageExpr = expr;
	return;
	
      case VALIDATOR_MESSAGE:
	if (expr != null && expr.isLiteralText())
	  _validatorMessage = (String) expr.getValue(null);
	else
	  _validatorMessageExpr = expr;
	return;
      }
    }

    super.setValueExpression(name, expr);
  }

  //
  // EditableValueHolder properties.
  //

  public boolean isRequired()
  {
    if (_required != null)
      return _required;
    else if (_requiredExpr != null)
      return Util.evalBoolean(_requiredExpr, getFacesContext());
    else
      return false;
  }

  public void setRequired(boolean required)
  {
    _required = required;
  }

  public boolean isImmediate()
  {
    if (_immediate != null)
      return _immediate;
    else if (_immediateExpr != null)
      return Util.evalBoolean(_immediateExpr, getFacesContext());
    else
      return false;
  }

  public void setImmediate(boolean immediate)
  {
    _immediate = immediate;
  }

  public Object getSubmittedValue()
  {
    return _submittedValue;
  }

  public void setSubmittedValue(Object submittedValue)
  {
    _submittedValue = submittedValue;
  }

  public void setValue(Object value)
  {
    super.setValue(value);

    setLocalValueSet(true);
  }

  public boolean isLocalValueSet()
  {
    return _isLocalValueSet;
  }

  public void setLocalValueSet(boolean isSet)
  {
    _isLocalValueSet = isSet;
  }

  public void resetValue()
  {
    setValue(null);
    setSubmittedValue(null);
    setLocalValueSet(false);
    setValid(true);
  }

  public boolean isValid()
  {
    return _isValid;
  }
  
  public void setValid(boolean valid)
  {
    _isValid = valid;
  }

  @Deprecated
  public MethodBinding getValueChangeListener()
  {
    FacesListener []listeners = getFacesListeners(FacesListener.class);
    for (int i = 0; i < listeners.length; i++) {
      if (listeners[i] instanceof ValueChangeListenerAdapter) {
	ValueChangeListenerAdapter adapter
	  = (ValueChangeListenerAdapter) listeners[i];

	return adapter.getBinding();
      }
    }

    return null;
  }

  @Deprecated
  public void setValueChangeListener(MethodBinding binding)
  {
    ValueChangeListener listener
      = new ValueChangeListenerAdapter(binding);

    FacesListener []listeners = getFacesListeners(FacesListener.class);
    for (int i = 0; i < listeners.length; i++) {
      if (listeners[i] instanceof ValueChangeListenerAdapter) {
	removeFacesListener(listeners[i]);
      }
    }
    
    addValueChangeListener(listener);
  }

  public void addValidator(Validator validator)
  {
    Validator []newValidators = new Validator[_validators.length + 1];
    System.arraycopy(_validators, 0, newValidators, 0, _validators.length);
    newValidators[_validators.length] = validator;

    _validators = newValidators;
  }

  public void removeValidator(Validator validator)
  {
    int length = _validators.length;
    for (int i = 0; i < length; i++) {
      if (_validators[i] == validator) {
	Validator []newValidators = new Validator[length - 1];
	System.arraycopy(_validators, 0, newValidators, 0, i);
	System.arraycopy(_validators, i + 1, newValidators, i, length - i - 1);
	_validators = newValidators;
	return;
      }
    }
  }

  public Validator []getValidators()
  {
    return _validators;
  }

  @Deprecated
  public MethodBinding getValidator()
  {
    int length = _validators.length;
    for (int i = 0; i < length; i++) {
      Validator validator = _validators[i];

      if (validator instanceof ValidatorAdapter) {
	ValidatorAdapter adapter = (ValidatorAdapter) validator;

	return adapter.getBinding();
      }
    }

    return null;
  }

  @Deprecated
  public void setValidator(MethodBinding binding)
  {
    ValidatorAdapter adapter = new ValidatorAdapter(binding);
    
    int length = _validators.length;
    for (int i = 0; i < length; i++) {
      Validator validator = _validators[i];

      if (validator instanceof ValidatorAdapter) {
	_validators[i] = adapter;
	return;
      }
    }

    addValidator(adapter);
  }

  public void addValueChangeListener(ValueChangeListener listener)
  {
    addFacesListener(listener);
  }
  
  public void removeValueChangeListener(ValueChangeListener listener)
  {
    removeFacesListener(listener);
  }
  
  public ValueChangeListener []getValueChangeListeners()
  {
    return
      (ValueChangeListener []) getFacesListeners(ValueChangeListener.class);
  }

  //
  // processing
  //

  @Override
  public void decode(FacesContext context)
  {
    setValid(true);
    super.decode(context);
  }

  public void processDecodes(FacesContext context)
  {
    if (isRendered()) {
      super.processDecodes(context);

      if (isImmediate()) {
	try {
	  validate(context);
	} catch (RuntimeException e) {
	  context.renderResponse();

	  throw e;
	}

	if (! isValid())
	  context.renderResponse();
      }
    }
  }

  public void processUpdates(FacesContext context)
  {
    if (isRendered()) {
      super.processUpdates(context);

      try {
	updateModel(context);
      } catch (RuntimeException e) {
	context.renderResponse();
      
	throw e;
      }

      if (! isValid())
	context.renderResponse();
    }
  }

  public void updateModel(FacesContext context)
  {
    if (! isValid())
      return;

    if (! isLocalValueSet())
      return;

    if (_valueExpr == null)
      return;

    try {
      _valueExpr.setValue(context.getELContext(), getLocalValue());

      setValue(null);
      setLocalValueSet(false);
    } catch (RuntimeException e) {
      log.log(Level.FINE, e.toString(), e);
      
      setValid(false);
      
      String summary = Util.l10n(context, UPDATE_MESSAGE_ID,
				 "{0}: An error occurred while processing your submitted information.",
				 Util.getLabel(context, this));

      String detail = summary;

      FacesMessage msg = new FacesMessage(summary, detail);

      context.addMessage(getClientId(context), msg);
    }
  }

  @Override
  public void processValidators(FacesContext context)
  {
    if (isRendered()) {
      super.processValidators(context);

      try {
	if (! isImmediate())
	  validate(context);

	if (! isValid())
	  context.renderResponse();
      } catch (RuntimeException e) {
	context.renderResponse();
      
	throw e;
      }
    }
  }

  public void validate(FacesContext context)
  {
    Object submittedValue = getSubmittedValue();
    if (submittedValue == null)
      return;

    Object value;

    try {
      value = getConvertedValue(context, submittedValue);

      validateValue(context, value);

      if (! isValid()) {
	context.renderResponse();
	return;
      }
    } catch (ConverterException e) {
      log.log(Level.FINE, e.toString(), e);
      setValid(false);
      context.renderResponse();

      FacesMessage msg = e.getFacesMessage();
      if (msg != null)
	context.addMessage(getClientId(context), msg);
      else {
	String summary = Util.l10n(context, CONVERSION_MESSAGE_ID,
				   "{0}: Conversion error occurred.",
				   Util.getLabel(context, this));

	String detail = summary;

	msg = new FacesMessage(summary, detail);
	
	context.addMessage(getClientId(context), msg);
      }
      return;
    }
    
    Object oldValue = getValue();
    setValue(value);
    setSubmittedValue(null);

    if (compareValues(oldValue, value)
	&& getFacesListeners(FacesListener.class).length > 0) {
      ValueChangeEvent event = new ValueChangeEvent(this, oldValue, value);

      broadcast(event);
    }
  }

  protected Object getConvertedValue(FacesContext context,
				     Object submittedValue)
    throws ConverterException
  {
    Renderer renderer = getRenderer(context);

    if (renderer != null)
      return renderer.getConvertedValue(context, this, submittedValue);
    else if (submittedValue instanceof String) {
      Converter converter = getConverter();

      if (converter != null)
	return converter.getAsObject(context, this, (String) submittedValue);

      if (_valueExpr != null) {
	Class type = _valueExpr.getType(context.getELContext());

	if (type != null) {
	  converter = context.getApplication().createConverter(type);

	  if (converter != null) {
	    return converter.getAsObject(context,
					 this,
					 (String) submittedValue);
	  }
	}
      }
    }

    return submittedValue;
  }
  
  protected boolean compareValues(Object oldValue, Object newValue)
  {
    if (oldValue == newValue)
      return false;
    else if (oldValue == null || newValue == null)
      return true;
    else
      return ! oldValue.equals(newValue);
  }

  public void validateValue(FacesContext context, Object value)
  {
    if (! isValid()) {
    }
    else if (value != null && ! "".equals(value)) {
      for (Validator validator : getValidators()) {
	try {
	  validator.validate(context, this, value);
	} catch (ValidatorException e) {
	  log.log(Level.FINER, e.toString(), e);

	  if (e.getFacesMessage() != null)
	    context.addMessage(getClientId(context), e.getFacesMessage());
	  else {
	    FacesMessage msg = new FacesMessage(e.getMessage(), e.toString());
	    context.addMessage(getClientId(context), msg);
	  }
	  
	  setValid(false);
	}
      }
    }
    else if (isRequired()) {
      String summary = Util.l10n(context, REQUIRED_MESSAGE_ID,
				 "{0}: UIInput validation Error: Value is required.",
				 Util.getLabel(context, this));

      String detail = summary;

      FacesMessage msg = new FacesMessage(summary, detail);
	
      context.addMessage(getClientId(context), msg);

      setValid(false);
      return;
    }

  }

  //
  // state
  //

  public Object saveState(FacesContext context)
  {
    Object []savedValidators = null;

    if (_validators.length > 0) {
      savedValidators = new Object[2 * _validators.length];

      for (int i = 0; i < _validators.length; i++) {
	Validator validator = _validators[i];

	int index = 2 * i;
	
	savedValidators[index] = validator.getClass();

	if (validator instanceof StateHolder) {
	  StateHolder holder = (StateHolder) validator;
	  
	  savedValidators[index + 1] = holder.saveState(context);
	}
      }
    }
    
    return new Object[] {
      super.saveState(context),
      _immediate,
      Util.save(_immediateExpr, context),
      _required,
      Util.save(_requiredExpr, context),
      _requiredMessage,
      Util.save(_requiredMessageExpr, context),
      _converterMessage,
      Util.save(_converterMessageExpr, context),
      _validatorMessage,
      Util.save(_validatorMessageExpr, context),
      savedValidators,
    };
  }

  public void restoreState(FacesContext context, Object value)
  {
    Object []state = (Object []) value;

    super.restoreState(context, state[0]);

    _immediate = (Boolean) state[1];
    _immediateExpr = Util.restoreBoolean(state[2], context);

    _required = (Boolean) state[3];
    _requiredExpr = Util.restoreBoolean(state[4], context);

    _requiredMessage = (String) state[5];
    _requiredMessageExpr = Util.restoreString(state[6], context);

    _converterMessage = (String) state[7];
    _converterMessageExpr = Util.restoreString(state[8], context);

    _validatorMessage = (String) state[9];
    _validatorMessageExpr = Util.restoreString(state[10], context);
    
    _valueExpr = super.getValueExpression("value");

    Object []savedValidators = (Object []) state[11];

    if (savedValidators != null) {
      _validators = new Validator[savedValidators.length / 2];

      for (int i = 0; i < _validators.length; i++) {
	int index = 2 * i;
	
	Class cl = (Class) savedValidators[index];

	try {
	  Validator validator = (Validator) cl.newInstance();

	  if (validator instanceof StateHolder) {
	    StateHolder holder = (StateHolder) validator;

	    holder.restoreState(context, savedValidators[index + 1]);
	  }

	  _validators[i] = validator;
	} catch (Exception e) {
	  throw new FacesException(e);
	}
      }
    }
  }

  //
  // private helpers
  //

  private static enum PropEnum {
    IMMEDIATE,
    REQUIRED,
    VALUE,
    REQUIRED_MESSAGE,
    CONVERTER_MESSAGE,
    VALIDATOR_MESSAGE,
  }

  private static class ValueChangeListenerAdapter
    implements ValueChangeListener {
    private MethodBinding _binding;

    public ValueChangeListenerAdapter(MethodBinding binding)
    {
      _binding = binding;
    }

    MethodBinding getBinding()
    {
      return _binding;
    }

    public void processValueChange(ValueChangeEvent event)
      throws AbortProcessingException
    {
      FacesContext facesContext = FacesContext.getCurrentInstance();

      _binding.invoke(facesContext, new Object[] { event }); 
    }

    public String toString()
    {
      return "ValueChangeListenerAdapter[" + _binding + "]";
    }
  }

  private static class ValidatorAdapter
    implements Validator {
    private MethodBinding _binding;

    public ValidatorAdapter(MethodBinding binding)
    {
      _binding = binding;
    }

    MethodBinding getBinding()
    {
      return _binding;
    }

    public void validate(FacesContext context,
			 UIComponent component,
			 Object value)
      throws ValidatorException
    {
      FacesContext facesContext = FacesContext.getCurrentInstance();

      _binding.invoke(facesContext, new Object[] { context, component, value }); 
    }

    public String toString()
    {
      return "ValueChangeListenerAdapter[" + _binding + "]";
    }
  }

  static {
    _propMap.put("value", PropEnum.VALUE);
    _propMap.put("immediate", PropEnum.IMMEDIATE);
    _propMap.put("required", PropEnum.REQUIRED);
    _propMap.put("requiredMessage", PropEnum.REQUIRED_MESSAGE);
    _propMap.put("converterMessage", PropEnum.CONVERTER_MESSAGE);
    _propMap.put("validatorMessage", PropEnum.VALIDATOR_MESSAGE);
  }
}
