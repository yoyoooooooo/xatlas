$input v_normal, v_texcoord0

#include <bgfx_compute.sh>
#include "shared.h"
#include "rayBundleFragment.sh"

#define DEBUG_RAY_BUNDLE 0

SAMPLER2D(s_lightmapPrevPass, 0);
SAMPLER2D(s_diffuse, 1);
SAMPLER2D(s_emission, 2);

FRAMEBUFFER_UIMAGE2D_RW(s_atomicCounter, r32ui, 0);
FRAMEBUFFER_UIMAGE2D_RW(s_rayBundleHeader, r32ui, 1);
FRAMEBUFFER_UIMAGE2D_RW(s_rayBundleData, rgba32ui, 2);

#if DEBUG_RAY_BUNDLE
FRAMEBUFFER_IMAGE2D_RW(s_rayBundleDebugWrite, rgba8, 3);
#endif

uniform vec4 u_diffuse;
uniform vec4 u_emission;
uniform vec4 u_shade_diffuse_emission;
#define u_diffuseType uint(u_shade_diffuse_emission.y)
#define u_emissionType uint(u_shade_diffuse_emission.z)
uniform vec4 u_pass;
uniform vec4 u_lightmapSize_dataSize;
#define u_dataSize uint(u_lightmapSize_dataSize.z)

ivec2 rayBundleDataUv(uint offset, uint pixel)
{
	return ivec2((offset * 2u + pixel) % u_dataSize, (offset * 2u + pixel) / u_dataSize);
}

void main()
{
	vec3 color;
	if (uint(u_pass.x) == 0u) {
		vec3 emission = u_emission.rgb;
		if (u_emissionType == EMISSION_TEXTURE)
			emission = texture2D(s_emission, v_texcoord0.xy).rgb;
		color = emission;
	}
	else {
		if (u_emission.r > 0.0 || u_emission.g > 0.0 || u_emission.b > 0.0) {
			// Render emissive surfaces as black in bounce passes.
			color = vec3_splat(0.0);
		}
		else {
			vec3 diffuse = u_diffuse.rgb;
			if (u_diffuseType == DIFFUSE_TEXTURE)
				diffuse *= texture2D(s_diffuse, v_texcoord0.xy).rgb;
			color = diffuse.rgb * texture2D(s_lightmapPrevPass, v_texcoord0.zw).rgb;
		}
	}
	uint newOffset = imageAtomicAdd(s_atomicCounter, ivec2(0, 0), 1u);
	if (newOffset >= u_dataSize * u_dataSize * 3u) {
		discard;
		return;
	}
	uint oldOffset = imageAtomicExchange(s_rayBundleHeader, ivec2(gl_FragCoord.xy), newOffset);
	RayBundleFragmentData fragmentData = encodeRayBundleFragment(color, oldOffset, v_normal, gl_FragCoord.z, v_texcoord0.zw * u_lightmapSize_dataSize.xy);
	imageStore(s_rayBundleData, rayBundleDataUv(newOffset, 0u), fragmentData.data0);
	imageStore(s_rayBundleData, rayBundleDataUv(newOffset, 1u), fragmentData.data1);
#if DEBUG_RAY_BUNDLE
	//imageStore(s_rayBundleDebugWrite, ivec2(gl_FragCoord.xy), vec4(vec3_splat(float(newOffset) / (1024.0 * 1024.0)), 1.0));
	imageStore(s_rayBundleDebugWrite, ivec2(gl_FragCoord.xy), vec4(1.0, 0.0, 1.0, 1.0));
	//imageStore(s_rayBundleDebugWrite, ivec2(v_texcoord0.z * u_lightmapSize_dataSize.x, v_texcoord0.w * u_lightmapSize_dataSize.y), vec4(1.0, 0.0, 1.0, 1.0));
#endif
}
