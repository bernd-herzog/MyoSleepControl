package de.nachregenkommtsonne.myoarengine.utility;

public class Vector
{
  private float[] _values = new float[3];

  public Vector(float x, float y, float z)
  {
    _values[0] = x;
    _values[1] = y;
    _values[2] = z;
  }

  public Vector()
  {
  }

  public Vector(Vector vector)
  {
    _values = vector._values.clone();
  }

  public Vector(float values[])
  {
    if (values.length != 3)
      throw new RuntimeException("Not a 3 dimensional vector");

    _values = values.clone();
  }

  public float getX()
  {
    return _values[0];
  }

  public float getY()
  {
    return _values[1];
  }

  public float getZ()
  {
    return _values[2];
  }

  public Vector add(Vector vector)
  {
    return new Vector(_values[0] + vector._values[0], _values[1] + vector._values[1], _values[2] + vector._values[2]);
  }

  public Vector sub(Vector vector)
  {
    return new Vector(_values[0] - vector._values[0], _values[1] - vector._values[1], _values[2] - vector._values[2]);
  }

  public Vector cross(Vector vector)
  {
    Vector ret = new Vector(0, 0, 0);
    ret._values[0] = _values[1] * vector._values[2] - _values[2] * vector._values[1];
    ret._values[1] = _values[2] * vector._values[0] - _values[0] * vector._values[2];
    ret._values[2] = _values[0] * vector._values[1] - _values[1] * vector._values[0];
    return ret;
  }

  public float[] getValues()
  {
    return _values;
  }

  public Vector mult(float value)
  {
    return new Vector(_values[0] * value, _values[1] * value, _values[2] * value);
  }

  public Vector div(float value)
  {
    return new Vector(_values[0] / value, _values[1] / value, _values[2] / value);
  }

  public Vector normalize()
  {
    float length = getLength();
    return div(length);
  }

  public float getLength()
  {
    float squareLength = _values[0] * _values[0] + _values[1] * _values[1] + _values[2] * _values[2];
    return (float) Math.sqrt(squareLength);
  }

  public boolean isValid()
  {
    return !(Float.isNaN(_values[0]) || Float.isNaN(_values[1]) || Float.isNaN(_values[2]));
  }
}