#include <GLES/gl.h>
#include <GLES2/gl2.h>

#include "MyoArRenderer.h"
#include "Scripting.h"
#include "Model.h"
#include "ModelRenderer.h"
#include "ModelFactory.h"

MyoArRenderer::MyoArRenderer(ModelFactory *modelFactory, Scripting *scripting) {
	_scripting = scripting;

	_modelRenderer = new ModelRenderer();

	_skyModel = modelFactory->CreateSkyModel();
	_floorModel = modelFactory->CreateFloorModel();
}
MyoArRenderer::~MyoArRenderer() {
}

void MyoArRenderer::InitializeViewport(SIZE size)
{
	_size = size;

	glViewport(0, 0, _size.cx, _size.cy);

	_scripting->SetUiSize(size);
}

void MyoArRenderer::DrawSkyBox()
{
	_modelRenderer->RenderModel(_skyModel);
}

void MyoArRenderer::InitializePerspective(float *rotationMatrix)
{
	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();

	float fW = 0.1f * _size.cx / _size.cy;
	glFrustumf( -fW, fW, -0.1f, 0.1f, 0.1f, 200.0f);

	glMatrixMode(GL_MODELVIEW);
	glLoadMatrixf(rotationMatrix);
}

void MyoArRenderer::Draw(float x, float y, float z, float *rotationMatrix)
{
	glClearColor(0.3f, 0.0f, 0.0f, 1.0f);
	glClear(GL_COLOR_BUFFER_BIT);
	glClear(GL_DEPTH_BUFFER_BIT);

	InitializePerspective(rotationMatrix);
	DrawSkyBox();

	glTranslatef(x, y, z);
	DrawWorld();

	InitializeHudPerspective();
	_scripting->RenderHUD();
}

void MyoArRenderer::DrawWorld()
{
	_modelRenderer->RenderModel(_floorModel);
}

void MyoArRenderer::InitializeHudPerspective()
{
	glDisable(GL_DEPTH_TEST);
	glClear(GL_DEPTH_BUFFER_BIT);

	glLoadIdentity();

	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	glOrthof(0.0f, (float)_size.cx, 0.0f, (float)_size.cy, -1.0f, 1.0f); // {0,0} ist unten links
	glMatrixMode(GL_MODELVIEW);

	glRotatef(-90, 0, 0, 1);
	glTranslatef(-_size.cy, 0, 0);
}
