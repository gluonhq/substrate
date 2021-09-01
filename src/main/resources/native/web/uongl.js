console.log("WELCOME UONGL!");



var FLOATS_PER_TC=2 * 4;
var FLOATS_PER_VC=3 * 4;
var FLOATS_PER_VERT=(FLOATS_PER_TC * 2 + FLOATS_PER_VC);
var coordStride= FLOATS_PER_VERT;
var colorStride=4;

var buffers = [];
var bufferIdx = 0;
var scissorEnabled = false;
var depthWritesEnabled = false;

var gl = null;

function wgl() {
    if (gl != null) {
        return gl;
    }
    var canvas = document.getElementById("jfxcanvas");
    gl = canvas.getContext("webgl2");
/*
    function logGLCall(functionName, args) {
       console.log("dbgl." + functionName + "(" +
          WebGLDebugUtils.glFunctionArgsToString(functionName, args) + ")");
    }
    gl = WebGLDebugUtils.makeDebugContext(gl, undefined, logGLCall);
*/
    return gl;
}

function buff(p) {
    bufferIdx++;
    buffers[bufferIdx] = p;
    return bufferIdx;
}

function native_com_sun_prism_es2_GLFactory_nGetGLVendor(ptr) {
    console.log("nGetGLVendor asked");
    return "GluonWebGL";
}

function native_com_sun_prism_es2_GLFactory_nGetGLRenderer(ptr) {
    console.log("nGetGLRenderer asked");
    return "GluonWebRenderer";
}

function native_com_sun_prism_es2_GLFactory_nGetGLVersion(ptr) {
    console.log("nGetGLVersion asked");
    return "GluonWebVersion";
}

// COVERED
function native_com_sun_prism_es2_GLFactory_nIsGLExtensionSupported(ptr, a) {
    console.log("NISGLEXTENSIONSUPPOERTED!!! a = " + a);
   if (a == "GL_EXT_texture_format_BGRA8888") return false;
   if (a == "GL_ARB_multisample") return false;
    console.log("assume true");
    return true;
}

function native_com_sun_glass_ui_web_WebApplication__invokeAndWait(r) {
    console.log("INVOKEANDWAIT!" + r);
    r.run__V();
    console.log("INVOKEANDWAIT DONE!" + r);
}

// ------------
// WEBGLVIEW 
// ------------

function native_com_sun_glass_ui_web_WebGLView__getNativeView(ptr) {
    console.log("[UONGL] getNetiveView, ptr = " + ptr);
    return 1;
}

function native_com_sun_glass_ui_web_WebGLView__getX(ptr) {
    console.log("[UONGL] getX, ptr = " + ptr);
    return 0;
}

function native_com_sun_glass_ui_web_WebGLView__getY(ptr) {
    console.log("[UONGL] getY, ptr = " + ptr);
    return 0;
}

// ------------
// WEBWINDOW 
// ------------

function native_com_sun_glass_ui_web_WebWindow__setAlpha(ptr, alpha) {
    console.log("[UONGL] setApha, ptr = " + ptr+" and alpha = " +alpha);
    var canvas = document.getElementById("jfxcanvas");
    var ctx = canvas.getContext("webgl2");
    ctx.globalAlpha = alpha;
}

function native_com_sun_glass_ui_web_WebWindow__setIcon(ptr) {
    console.log("[UONGL] setIcon, ptr = " + ptr);
}

function native_com_sun_glass_ui_web_WebWindow__setResizable(ptr, resizable) {
    console.log("[UONGL] setResizable, ptr = " + ptr+" and r = " +resizable);
}

function native_com_sun_glass_ui_web_WebWindow__setFocusable(ptr, f) {
    console.log("[UONGL] setFocusable, ptr = " + ptr+" and f = " +f);
}

function native_com_sun_glass_ui_web_WebWindow__requestFocus(ptr, evt) {
    console.log("[UONGL] requestFocus, ptr = " + ptr+" and evt = " +evt);
}

function native_com_sun_glass_ui_web_WebWindow__setBackground(ptr, r, g, b) {
    console.log("[UONGL] setBGColor, ptr = " + ptr+" and r = " +r+", g = " +g+", b = " +b);
    var red = 256 * r;
    var green = 256 * g;
    var blue = 256*b;
    var gl = wgl();
    var canvas = document.getElementById("jfxcanvas");
    canvas.style.backgroundColor = 'rgb('+red+','+ green+','+ blue+')';
}

function native_com_sun_glass_ui_web_WebWindow__setVisible(ptr, vis) {
    console.log("[UONGL] setVisible, ptr = " + ptr+" and vis = " +vis);
}

function native_com_sun_glass_ui_web_WebWindow__setMinimumSize(ptr, width, height) {
    console.log("[UONGL] setMinimumSize to "+ width+", " + height);
}

function native_com_sun_glass_ui_web_WebWindow__setMaximumSize(ptr, width, height) {
    console.log("[UONGL] setMaximumSize to "+ width+", " + height);
}

function native_com_sun_glass_ui_web_WebWindow__setView(ptr, view) {
    console.log("[UONGL] setView to "+ view);
}

function native_com_sun_glass_ui_web_WebGLView__setParent(ptr, parentptr) {
    console.log("[UONGL] setParentPtr ");
}

// ------------
// GLCONTEXT 
// ------------

// COVERED
function native_com_sun_prism_es2_GLContext_nActiveTexture(ptr, texUnit) {
    console.log("[UONGL] nActiveTexture ctx = "+ptr+", id = "+texUnit);
    var gl = wgl();
    gl.activeTexture(gl.TEXTURE0+texUnit);
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nBindFBO(nativeCtxInfo, nativeFBOID) {
    console.log("[UONGL] nBindFBO ctx = "+nativeCtxInfo+", id = "+nativeFBOID);
    var gl = wgl();
    if (nativeFBOID == 0) {
        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    } else {
        var buffer = buffers[nativeFBOID];
        gl.bindFramebuffer(gl.FRAMEBUFFER, buffer);
    }
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nBindTexture(nativeCtxInfo, texId) {
console.log("UONGL bindTexture with id "+texId);
    var gl = wgl();
    var tex = buffers[texId];
    gl.bindTexture(gl.TEXTURE_2D, tex);
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nBlendFunc(cf, df) {
console.log("UONGL bbendFunv with c = "+cf+" and d = " + df);
    var gl = wgl();
    gl.blendFunc(getConstant(cf), getConstant(df));
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nClearBuffers (ctxInfo,
        red, green, blue, alpha,
        clearColor, clearDepth, ignoreScissor){
console.log("UONGL clearBuffers, cc = " + clearColor+", cd = " + clearDepth+", is = " + ignoreScissor);

    var gl = wgl();
    if (ignoreScissor && scissorEnabled) {
console.log("Scissor enabled, but we will ignore it");
        // glClear() honors the current scissor, so disable it
        // temporarily if ignoreScissor is true
        gl.disable(gl.SCISSOR_TEST);
    } else {
console.log("no Scissor action needed");
    }

    var clearBIT = 0;
    if (clearColor) {
        clearBIT = gl.COLOR_BUFFER_BIT;
        gl.clearColor(red, green, blue, alpha);
    }

    if (clearDepth) {
        clearBIT  = clearBIT| gl.DEPTH_BUFFER_BIT;
        // also make sure depth writes are enabled for the clear operation
        if (depthWritesEnabled) {
            glDepthMask(true);
        }
        gl.clear(clearBIT);
        if (depthWritesEnabled) {
            glDepthMask(false);
        }
    } else {
        gl.clear(clearBIT);
    }

    if (ignoreScissor && scissorEnabled) {
console.log("Scissor enabled, but we ignored it, restore now");
        gl.enable(gl.SCISSOR_TEST);
    }
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nCompileShader(ptr, src, vert) {
    console.log("[UONGL] compile shader \n"+src);
    var gl = wgl();
    var shader;
    if (vert) {
        shader = gl.createShader(gl.VERTEX_SHADER);
    } else {
        shader = gl.createShader(gl.FRAGMENT_SHADER);
    }
    gl.shaderSource(shader, src);
    gl.compileShader(shader);
    var msg = gl.getShaderInfoLog(shader);
    if (msg.length >0) {
console.log("ERROR! " + msg);
    }
    answer = buff(shader);
    glErr(gl);
    return answer;
}

// COVERED
function native_com_sun_prism_es2_GLContext_nCreateFBO (ptr, texId) {
    console.log("[UONGL] ncreateFBO ");
    var gl = wgl();
    var buffer = gl.createFramebuffer();
    var tex = buffers[texId];
    gl.bindFramebuffer(gl.FRAMEBUFFER, buffer)
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
console.log("[UONGL] createFBO will return buffer " + buffer);
    answer = buff(buffer);
    glErr(gl);
    return answer;
}

// COVERED
function native_com_sun_prism_es2_GLContext_nCreateIndexBuffer16 (ptr, data, n) {
    var gl = wgl();
    var buffer = gl.createBuffer();
    console.log("[UONGL] ncreateIndexBuffer16, n = "+n);
    var sba = new Uint16Array(data);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, sba, gl.STATIC_DRAW);
    console.log("[UONGL] BUFFER will return " + buffer);
    bufferIdx++;
    buffers[bufferIdx] = buffer;
    glErr(gl);
    return bufferIdx;
}

// COVERED
function native_com_sun_prism_es2_GLContext_nCreateProgram(ptr, vertID, fragIDArr, numAttrs, attrs, indexs) {
    var gl = wgl();
    console.log("[UONGL] ncreateProgram");
    var program = gl.createProgram();
    var answer = buff(program);
    var vertexShader = buffers[vertID];
    gl.attachShader(program, vertexShader);
    for (i = 0 ; i < fragIDArr.length; i++ ) {
        var fragShader = buffers[fragIDArr[i]];
        gl.attachShader(program, fragShader);
    }
    for (i = 0; i < numAttrs; i++) {
console.log("[UONGL] bindAttribloc " + attrs[i]+" to " + indexs[i]);
        gl.bindAttribLocation(program, indexs[i], attrs[i]);
    }
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        var det = gl.getProgramInfoLog(program);
        console.error("Error compiling shader: \n" + det);
    } else {
        console.log("[UONGL] shader program compiled!");
    }
    glErr(gl);
    return answer;
}

// COVERED
function native_com_sun_prism_es2_GLContext_nCreateTexture (ptr, width, height) {
    var gl = wgl();
    console.log("[UONGL] ncreateTexture w = "+width+", h = "+height);
    var texture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, width, height, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4 * width * height));
    var answer = buff(texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    console.log("[UONGL] ncreateTexture created texture with id " + answer);
    glErr(gl);
    return answer;
}

// COVERED
function native_com_sun_prism_es2_GLContext_nDrawIndexedQuads(ptr, numVertices, dataf, datab) {
    var gl = wgl();
    console.log("[UONGL] nDrawIndexedQuads nv = "+numVertices+", df = " + dataf.length+", db = " + datab.length);
    var floatBuffer = gl.createBuffer();
    var rawFloatBuffer = new Float32Array(dataf);
    gl.bindBuffer(gl.ARRAY_BUFFER, floatBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, rawFloatBuffer, gl.STATIC_DRAW);
    gl.vertexAttribPointer(0, 3, gl.FLOAT, false, coordStride, 0);
    gl.vertexAttribPointer(2, 2, gl.FLOAT, false, coordStride, FLOATS_PER_VC);
    gl.vertexAttribPointer(3, 2, gl.FLOAT, false, coordStride, (FLOATS_PER_VC + FLOATS_PER_TC));
    var byteBuffer = gl.createBuffer();
    var rawByteBuffer = new Uint8Array(datab);
    gl.bindBuffer(gl.ARRAY_BUFFER, byteBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, rawByteBuffer, gl.STATIC_DRAW);
    gl.vertexAttribPointer(1, 4, gl.UNSIGNED_BYTE, true, colorStride, 0);
        // ctx->vbFloatData = pFloat;
// ctx->vbByteData = pByte;
    var numQuads = numVertices/4;
console.log("numQuads = "+numQuads);
// native_com_sun_prism_es2_GLContext_nDisableVertexAttributes(null);
gl.drawElements(gl.TRIANGLES, numQuads * 2 * 3, gl.UNSIGNED_SHORT, 0);
    console.log("[UONGL] nDrawIndexedQuads done");
    glErr(gl);

}

// COVERED
function native_com_sun_prism_es2_GLContext_nDisableVertexAttributes(ptr){
    var gl = wgl();
    for (i = 0; i < 4; i++) {
        gl.disableVertexAttribArray(i);
    }
    console.log("[UONGL] nDisableVertexAttr DONE");
    glErr(gl);
}

// COVERED
function native_com_sun_prism_es2_GLContext_nEnableVertexAttributes(ptr){
    var gl = wgl();
    for (i = 0; i < 4; i++) {
        gl.enableVertexAttribArray(i);
    }
    console.log("[UONGL] nEnableVertexAttr DONE");
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nGenAndBindTexture(ptr){
console.log("[UONGL] GenAndBindTexture");
    var gl = wgl();
    var texture = gl.createTexture();
    var tid = buff(texture);
    gl.bindTexture(gl.TEXTURE_2D, texture);
console.log("[UONGL] GenAndBindTexture created new texture with id " + tid);
    glErr(gl);
    return tid;

}

function native_com_sun_prism_es2_GLContext_nGetUniformLocation(ptr, pid, val){
console.log("[UONGL] GetUniformLocation for programId "+ pid+" and val = " + val);
    var gl = wgl();
    var answer = gl.getUniformLocation(buffers[pid], val);
console.log("result = " + answer);
    glErr(gl);
    return answer;
}

function native_com_sun_prism_es2_GLContext_nPixelStorei(pname, value) {
    var gl = wgl();
    var name = null;
    if (pname == 60) name = gl.UNPACK_ALIGNMENT;
    if (pname == 61) name = gl.UNPACK_ROW_LENGTH;
    if (pname == 62) name = gl.UNPACK_SKIP_PIXELS;
    if (pname == 63) name = gl.UNPACK_SKIP_ROWS;
    gl.pixelStorei(name, value);
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nScissorTest(ctx, enable, x, y, w, h) {
    console.log("scissortest, enable = " + enable+", x = " + x);
    var gl = wgl();
    if (enable == 1) {
        gl.enable(gl.SCISSOR_TEST);
        gl.scissor(x, y, w, h);
        scissorEnabled = true;
    } else {
        gl.disable(gl.SCISSOR_TEST);
        scissorEnabled = false;
    }
}

function native_com_sun_prism_es2_GLContext_nSetIndexBuffer(ptr, bufferId ) {
    var gl = wgl();
    console.log("[UONGL] nSetIndexBuffer ELEMENT_ARRAY_BUFFER to buffer with id "+ bufferId);
    var buffer = buffers[bufferId];
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, buffer);
    console.log("[UONGL] nSetIndexBuffer done to buffer "+ buffer);
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nTexImage2D0(target, level, 
 internalFormat, width, height, border, format, type,
        pixels, pixelsByteOffset, useMipmap) {
    var gl = wgl();
    console.log("[UONGL] nTexImage2D0 pbo = "+ pixelsByteOffset);
console.log("TARGET = " + target);
console.log("LEVEL = " + level);
console.log("FORMAT = " + format);
console.log("IF = " + internalFormat);
console.log("TYPE = " + type);
    if (useMipmap) {
        console.log("[UONGL] nTexImage2D0 MIPMAP!");
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_NEAREST);
    }
    // gl.texImage2D(getTextureConstant(target), level, getTextureConstant(internalFormat), width, height, 0, getTextureConstant(format), getTextureConstant(type), pixels);
    gl.texImage2D(getConstant(target), level, getConstant(format), width, height, 0, getConstant(format), getConstant(type), pixels);
    glErr(gl);
    return true;
}
function native_com_sun_prism_es2_GLContext_nUniformMatrix4fv(ptr, loc, transpose, values ) {
    var gl = wgl();
    console.log("[UONGL] nUniformMatrix4fv loc = "+ loc);
    gl.uniformMatrix4fv(loc, false, values);
    console.log("[UONGL] nUniformMatrix4fv DONE ");
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nUpdateViewport(ptr, x, y, w, h) {
    console.log("[UONGL] nUpdateViewport to "+x+", "+y+", "+w+", "+h);
    var gl =wgl();
    gl.viewport(x,y,w,h); 
    console.log("[UONGL] nUpdateViewport to "+x+", "+y+", "+w+", "+h+" DONE");
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nUpdateWrapState(ptr, texID, wrapMode) {
    var gl = wgl();
    var modeS = gl.REPEAT;
    var modeT = gl.REPEAT;
    if (wrapMode = 101) {
        modeS = gl.CLAMP_TO_EDGE;
        modeT = gl.CLAMP_TO_EDGE;
    }
    console.log("[UONGL] nUpdateWrapState mode = "+ wrapMode+" == " + modeS);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, modeS);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, modeT);
    glErr(gl);
}


function native_com_sun_prism_es2_GLContext_nUseProgram(ptr, programId ) {
    console.log("[UONGL] nUseProgram with id "+ programId);
    var gl = wgl();
    var program = buffers[programId];
    gl.useProgram(program);
    glErr(gl);
}

function native_com_sun_prism_es2_GLContext_nTexSubImage2D0(target, level,
        xoffset, yoffset, width, height, format, type, pixels, pixelsByteOffset) {
    var gl = wgl();
    var ptr = pixels.fld_java_nio_ByteBuffer_data;
    var rawUInt8Buffer = new Uint8Array(ptr);
    var gFormat = getConstant(format);
    var gTarget = getConstant(target);
    var gType = getConstant(type);
    gl.texSubImage2D(gTarget,  level,
            xoffset, yoffset,
            width, height, gFormat,
            gType, rawUInt8Buffer);
}


function native_com_sun_prism_es2_GLContext_nTexParamsMinMax(min, max) {
    var gl = wgl();
    var param = getConstant(min);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, param);
    param = getConstant(max);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, param);
}

function native_com_sun_prism_es2_GLContext_nUniform1i(ptr, loc, v0) {
    var gl = wgl();
    gl.uniform1i(loc, v0);
}

function native_com_sun_prism_es2_GLContext_nUniform1f(ptr, loc, v0) {
    var gl = wgl();
    gl.uniform1f(loc, v0);
}

function native_com_sun_prism_es2_GLContext_nUniform2f(ptr, loc, v0, v1) {
    var gl = wgl();
    gl.uniform2f(loc, v0, v1);
}

function native_com_sun_prism_es2_GLContext_nUniform3f(ptr, loc, v0, v1, v2) {
    var gl = wgl();
    gl.uniform3f(loc, v0, v1, v2);
}

function native_com_sun_prism_es2_GLContext_nUniform4f(ptr, loc, v0, v1, v2, v3) {
    var gl = wgl();
    gl.uniform4f(loc, v0, v1, v2, v3);
}

function native_com_sun_prism_es2_GLContext_nUniform4fv1(ptr, loc, count, value, valueByteOffset) {
    var gl = wgl();
    console.log("[UONGL] nUniform4fv1 to "+loc+", "+count+", "+value+", "+valueByteOffset);
    var rawFloatBuffer = new Float32Array(value, valueByteOffset, count);
    gl.uniform4fv(loc, rawFloatBuffer);
}

// ------------
// WEBGLCONTEXT 
// ------------

function native_com_sun_prism_es2_WebGLContext_getIntParam(param) {
    var gl = wgl();
console.log("[UONGL] getIntParam for " + param) ;
    var answer = 1;
    if (param == 120) {
        answer = gl.getParameter(gl.MAX_FRAGMENT_UNIFORM_VECTORS);
    }
    if (param == 122) {
        answer = gl.getParameter(gl.MAX_TEXTURE_IMAGE_UNITS);
    }
    if (param == 123) {
        answer = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    }
    if (param == 124) {
        answer = gl.getParameter(gl.MAX_VERTEX_ATTRIBS);
    }
    if (param == 125) {
        answer = 4 * gl.getParameter(gl.MAX_VARYING_VECTORS);
    }
    if (param == 127) {
        answer = gl.getParameter(gl.MAX_VERTEX_TEXTURE_IMAGE_UNITS);
    }
    if (param == 128) {
        answer = 4 * gl.getParameter(gl.MAX_VERTEX_UNIFORM_VECTORS);
    }
    console.log("[UONGL] getIntParam asked for " + param+" results in " + answer);
    return answer;
}
function native_com_sun_prism_es2_WebGLContext_nGetNativeHandle(nativeCtxInfo) {
    console.log("[UONGL] WebGLContext_nGetNativeHandle always return 1");
    return 1;
}

function native_com_sun_prism_es2_WebGLContext_nInitialize(nativeDInfo, nativePFInfo,
            nativeshareCtxHandle, vSyncRequest) {
    console.log("[UONGL] WebGLContext_nInitialize init and then return 1");
    var gl = wgl();
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.depthMask(false);
    gl.disable(gl.DEPTH_TEST);
    gl.clearColor(0,0,0,0);
    glErr(gl);
    return 1;
}

function native_com_sun_prism_es2_WebGLContext_nMakeCurrent() {
    console.log("[UONGL] WebGLContext_nMakeCurrent NO-OP");
}

function getConstant(src) {
    var gl = wgl();
    var answer = -1;
    if (src ==0) return gl.ZERO;
    if (src ==1) return gl.ONE;
    if (src ==2) return gl.SRC_COLOR;
    if (src ==3) return gl.ONE_MINUS_SRC_COLOR;
    if (src ==4) return gl.DST_COLOR;
    if (src ==5) return gl.ONE_MINUS_DST_COLOR;
    if (src ==6) return gl.SRC_ALPHA;
    if (src ==7) return gl.ONE_MINUS_SRC_ALPHA;
    if (src ==8) return gl.DST_ALPHA;
    if (src ==9) return gl.ONE_MINUS_DST_ALPHA;
    if (src ==10) return gl.CONSTANT_COLOR;
    if (src ==11) return gl.ONE_MINUS_CONSTANT_COLOR;
    if (src ==12) return gl.CONSTANT_ALPHA;
    if (src ==13) return gl.ONE_MINUS_CONSTANT_ALPHA;
    if (src ==14) return gl.SRC_ALPHA_SATURATE;
    if (src == 20) return gl.FLOAT;
    if (src == 21) return gl.UNSIGNED_BYTE;
    // if (src == 22) return gl.UNSIGNED_INT_8_8_8_8_REV;
    if (src == 22) return gl.UNSIGNED_INT_2_10_10_10_REV;
    if (src == 23) return gl.UNSIGNED_INT_8_8_8_8;
    if (src == 40) return gl.RGBA;
    if (src == 41) answer = gl.BGRA;
    if (src == 42) return gl.RGB;
    if (src == 43) return gl.LUMINANCE;
    if (src == 44) return gl.ALPHA;
    if (src == 45) return gl.RGBA32F;
    if (src == 50) return gl.TEXTURE_2D;
    if (src == 51) return gl.TEXTURE_BINDING_2D;
    if (src == 52) return gl.NEAREST;
    if (src == 53) return gl.LINEAR;
    if (src == 54) return gl.NEAREST_MIPMAP_NEAREST;
    if (src == 55) return gl.LINEAR_MIPMAP_LINEAR;
    console.log("NO TEXTURE CONSTANT FOUND for "+src);
    return answer;
}

function glErr(gl) {
    var err = gl.getError();
    if (err!= 0) {
        console.log("GL ERROR!" + err);
        console.error("WE HAVE a GL ERROR " + err);
        throw new Error("gl-error");
    }
}

function native_com_sun_javafx_iio_web_WebImageLoader_initNativeLoading() {
    console.log("[JS] WebImageLoader_initNativeLoading");
}

function native_com_sun_javafx_iio_web_WebImageLoader_disposeLoader() {
    console.log("[JS] WebImageLoader_disposeLoader");
}

function native_com_sun_javafx_iio_web_WebImageLoader_loadImage() {
    console.log("[JS] WebImageLoader_loadImage");
    return 1;
}
