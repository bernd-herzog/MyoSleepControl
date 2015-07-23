#pragma once
#include "Scripting.h"

class MyoArRenderer {
private:
	SIZE _size;
	int _texIDSky, _texIDRasen;
	Scripting *_scripting;

	void DrawSkyBox();
	void InitializePerspective(float *rotationMatrix);
	void DrawWorld();
	void InitializeHudPerspective();

public:
	MyoArRenderer(int texIDSky, int texIDRasen, Scripting *scripting);
	virtual ~MyoArRenderer();

	void InitializeViewport(SIZE size);
	void Draw(float x, float y, float z, float *rotationMatrix);
};