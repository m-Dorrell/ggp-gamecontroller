<?xml version="1.0" encoding="ISO-8859-1"?>

<!--
	should work for most chess like games (chess, checkers, ...)
	tries to detect the size of the board automatically
-->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="../generic/template.xsl"/>
	<xsl:import href="../generic/chess_board.xsl"/>
	
	<xsl:template name="print_state">
		<xsl:call-template name="print_chess_state"/>
	</xsl:template>
	
</xsl:stylesheet>
