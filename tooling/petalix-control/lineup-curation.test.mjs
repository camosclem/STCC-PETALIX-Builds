import assert from 'node:assert/strict';
import test from 'node:test';
import {previewPetalixCuration} from './lineup-curation.mjs';

const channels=[
  {id:'1',name:'TSN 5',group:'Sports'},
  {id:'2',name:'TSN 1 HD',group:'Sports'},
  {id:'3',name:'FanDuel Sports Network South',group:'Sports'},
  {id:'4',name:'FanDuel TV',group:'Sports'},
  {id:'5',name:'Classic Western Movies',group:'Movies'},
  {id:'6',name:'AMC West',group:'Movies'},
  {id:'7',name:'ESPN 2',group:'Sports'},
  {id:'8',name:'ESPN 10',group:'Sports'},
  {id:'9',name:'Western Sports Network',group:'Sports'},
  {id:'10',name:'ABC',group:'Entertainment'}
];
const input={brand:'petalix',revision:'sample-42',channels};
test('preserves all channel IDs and original records',()=>{
  const output=previewPetalixCuration(input);
  assert.equal(output.channelCount,channels.length);
  assert.deepEqual(new Set(output.assignments.map(c=>c.id)),new Set(channels.map(c=>c.id)));
  assert.equal(channels[0].group,'Sports');
  assert.ok(output.groups.some(g=>g.name==='Sports'));
  assert.ok(output.groups.every(g=>!['Sports Events','Team Feeds','League Pass'].includes(g.name)));
});
test('orders TSN numerically, FanDuel TV first, and ordinary channels naturally',()=>{
  const output=previewPetalixCuration(input);
  const ids=group=>output.groups.find(g=>g.name===group).channelIds;
  assert.deepEqual(ids('TSN'),['2','1']);
  assert.deepEqual(ids('FanDuel'),['4','3']);
  assert.deepEqual(ids('Sports'),['7','8','9']);
});
test('Western movie channel does not confuse West Coast or sports feed',()=>{
  const assignments=previewPetalixCuration(input).assignments;
  const group=id=>assignments.find(c=>c.id===id).group;
  assert.equal(group('5'),'Western');
  assert.equal(group('6'),'Movies');
  assert.equal(group('9'),'Sports');
});
test('rejects wrong brand, missing revision, duplicate IDs',()=>{
  assert.throws(()=>previewPetalixCuration({...input,brand:'stcc'}));
  assert.throws(()=>previewPetalixCuration({...input,revision:''}));
  assert.throws(()=>previewPetalixCuration({...input,channels:[channels[0],channels[0]]}));
});
