"""Measured control checks, not aesthetic acceptance."""
import csv,json
def validate(output):
    captures=json.loads((output/'screenshots/captures.json').read_text());comparison=[r for r in captures if r['kind']=='comparison'];assert len(comparison)==12
    fixed=['x','y','z','yaw','pitch','fov','brightness_gamma','framebuffer_width','framebuffer_height','view_distance_chunks','simulation_distance_chunks','graphics','ambient_occlusion','dimension_ambient_light','dimension_fixed_time','light_mode']
    for view in ['tunnel','platform','edge','overlook']:
        rows=[r for r in comparison if r['view']==view];assert len(rows)==3 and {r['render_profile'] for r in rows}=={'a','b','c'}
        assert all([r[k] for k in fixed]==[rows[0][k] for k in fixed] for r in rows),view
        if view=='overlook':assert rows[0]['y']==55,'Creative overlook camera fell instead of holding position'
    for r in captures:
        e=r['environment'];assert e['profile']==r['render_profile'] and e['observed_lightmap_writes']>0;assert r['gl_error_at_capture']==0,r['file']
        if r['render_profile']=='a' or r['kind']=='dimension_scope':assert not e['custom_active'] and e['native_lightmap_rgb']==e['display_lightmap_rgb'],r['file']
        else:assert e['custom_active'] and e['display_lightmap_0_rgb']!=e['native_lightmap_0_rgb']
        if r['kind']=='natural_without_sources':assert e['glow_faces_drawn_last_frame']==0
    audits=[json.loads((output/f'audit-glimmer-{p}.json').read_text()) for p in ['a','b','c']]
    assert all(a['native_light_profile_pass'] and a['route_and_rail_pass'] for a in audits)
    assert all(a['light_profile']==audits[0]['light_profile'] for a in audits);assert len({a['generated_terrain_sha256'] for a in audits})==1
    for p in ['b','c']:
        a=json.loads((output/f'audit-baseline-no-sources-{p}.json').read_text());assert a['native_light_profile_pass'] and all(s['block']==s['sky']==0 for s in a['light_profile'])
    keys=['render_profile','view','kind','sample_fps','median_frame_ms','p95_frame_ms','max_frame_ms','frames_over_100ms','sample_frames','chunk_debug']
    with (output/'performance.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=keys);w.writeheader();w.writerows({k:r[k] for k in keys} for r in captures)
    return {'same_view_conditions_pass':True,'native_light_unchanged_between_profiles':True,'zero_source_environment_control_pass':True,'overworld_render_scope_pass':True,'gl_error_checks_pass':True,'aesthetic_acceptance':'Pending user comparison of actual raw screenshots.'}
