#pragma once

//#define __cplusplus 201103L

#include <vector>
#include <functional>
#include <string>
#include "global.h"


class FunctionHelper
{
public:
	std::string luaFuncName;
	lua_CFunction staticClassMemberFunc;
};

template<class nestedClassType>
class LuaBinding
{
	static std::vector<FunctionHelper *> someVector;
	static std::string className;
	static std::function<void (nestedClassType *)> callback;

public:
	template<int(nestedClassType::*SomeMember)(lua_State *L)>
	static void registerFunc(std::string methodName); // should be called only once

	template <int (nestedClassType::*TMethod)(lua_State *L)>
	static int LUA_FUNCTION method_stub(lua_State *L); // selector

	static void InitOnce(std::string className, std::function<void (nestedClassType *)> callback);
	static void Init(lua_State *L, std::string createFunctionName); // for every lua context
	static int LUA_FUNCTION index(lua_State *L);
	static int LUA_FUNCTION index_indirect(lua_State *L);
	static int LUA_FUNCTION CreateClass(lua_State *L);
};


template<class nestedClassType>
/* static */ std::vector<FunctionHelper *> LuaBinding<nestedClassType>::someVector;

template<class nestedClassType>
/* static */ std::string LuaBinding<nestedClassType>::className;

template<class nestedClassType>
std::function<void (nestedClassType *)> LuaBinding<nestedClassType>::callback;


template<class nestedClassType>
/* static */ void LuaBinding<nestedClassType>::Init(lua_State *L, std::string createFunctionName) // for every lua context
{
	//new metatable

	const char *tableName = className.c_str();
	luaL_newmetatable(L, tableName);
	lua_pushcfunction(L, &LuaBinding<nestedClassType>::index);
	lua_setfield(L, -2, "__index");
	lua_pop(L, 1);

	//register create function
	lua_pushcfunction(L, &LuaBinding<nestedClassType>::CreateClass);
	lua_setglobal(L, createFunctionName.c_str());
}

template<class nestedClassType>
/* static */ void LuaBinding<nestedClassType>::InitOnce(std::string className, std::function<void (nestedClassType *)> callback) // should be called only once
{
	LuaBinding<nestedClassType>::className = className;
	LuaBinding<nestedClassType>::callback = callback; //TODO: f�r jeden lua context �ber closure �bergeben
}

template<class nestedClassType>
template<int(nestedClassType::*SomeMember)(lua_State *L)>
/*static*/ void LuaBinding<nestedClassType>::registerFunc(std::string methodName) // should be called only once
{
	FunctionHelper *a = new FunctionHelper();
	a->luaFuncName = methodName;
	a->staticClassMemberFunc = &method_stub<SomeMember>;
	someVector.push_back(a);
}

template<class nestedClassType>
/*static*/ int LUA_FUNCTION LuaBinding<nestedClassType>::CreateClass(lua_State *L)
{
	nestedClassType *element = new nestedClassType();

	nestedClassType **b = static_cast<nestedClassType **>(lua_newuserdata(L, sizeof(nestedClassType *)));

	*b = element;

	luaL_getmetatable(L, className.c_str());
	lua_setmetatable(L, -2);

	lua_pushvalue(L, -1);
	int refId = luaL_ref(L, LUA_REGISTRYINDEX);
	element->SetRefID(refId);

	callback(element);

	return 1;
}

template<class nestedClassType>
template <int (nestedClassType::*TMethod)(lua_State *L)>
/*static*/ int LUA_FUNCTION LuaBinding<nestedClassType>::method_stub(lua_State *L) // selector
{
	nestedClassType **obj = static_cast<nestedClassType **>(lua_touserdata(L, 1)); 
	return (*obj->*TMethod)(L);
}

template<class nestedClassType>
/*static*/ int LUA_FUNCTION LuaBinding<nestedClassType>::index(lua_State *L)
{
	// 2: string "SetPosition" // 2nd parameter
	// 1: userdata UITextField // first parameter

	//nestedClassType *obj = static_cast<nestedClassType *>(luaL_checkudata(L, -2, className.c_str()));
	const char *func = luaL_checkstring(L, 2);

	for(auto a : someVector)
	{
		if (strcmp(func, a->luaFuncName.c_str()) == 0)
		{
			lua_pushcfunction(L, a->staticClassMemberFunc);
			return 1;
		}
	}

	return LuaBinding<typename nestedClassType::Base>::index_indirect(L);
}

template<class nestedClassType>
/*static*/ int LUA_FUNCTION LuaBinding<nestedClassType>::index_indirect(lua_State *L)
{
	// 2: string "SetPosition" // 2nd parameter
	// 1: userdata UITextField // first parameter
	//nestedClassType *obj = static_cast<nestedClassType *>(lua_touserdata(L, -2));
	const char *func = luaL_checkstring(L, 2);

	for(auto a : someVector)
	{
		if (strcmp(func, a->luaFuncName.c_str()) == 0)
		{
			lua_pushcfunction(L, a->staticClassMemberFunc);
			return 1;
		}
	}
	return 0;
}
