function sumLoop(limit) {
	var sum = 0;
	var i = 0;
	while(i < limit) {
		sum = sum + i;
		i++;
	}
	return sum
}

var i = 0;
var sum = 0;
while (i < 10000) {
	sum = sumLoop(i);
	i++;
}

console.log(sum)