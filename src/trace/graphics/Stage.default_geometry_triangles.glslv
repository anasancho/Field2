#version 410
layout (triangles) in;
layout (triangle_strip) out;
layout (max_vertices = 3) out;

out vec4 ovcolor;
in vec4[] vcolor;

in float[] CD;

void main(void)
{
    int i;

    for (i = 0; i < gl_in.length(); i++)
    {
        gl_Position = gl_in[i].gl_Position;
        gl_ClipDistance[0] = CD[i];
        ovcolor = vcolor[i];
        EmitVertex();
    }

    EndPrimitive();
}